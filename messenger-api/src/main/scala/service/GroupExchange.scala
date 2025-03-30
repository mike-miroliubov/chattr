package org.chats
package service

import org.apache.pekko.actor.typed.scaladsl.{ActorContext, Behaviors}
import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope
import org.apache.pekko.cluster.sharding.typed.scaladsl.{Entity, EntityTypeKey}
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import org.chats.config.serialization.JsonSerializable
import org.chats.service.ClientActor.OutgoingMessage

object GroupExchange {
  val typeKey: EntityTypeKey[OutgoingMessage | Command] = EntityTypeKey("GroupExchange")

  // this enables sharding of Exchanges
  val shardRegion: ActorRef[ShardingEnvelope[OutgoingMessage | Command]] =
    sharding.init(Entity(typeKey)(createBehavior = entityContext => GroupExchange(entityContext.entityId, PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId))))

  sealed trait Command extends JsonSerializable
  final case class MakeGroup(owner: String, members: Set[String]) extends Command
  final case class AddMember(userId: String) extends Command
  final case class RemoveMember(userId: String) extends Command
  case object CloseGroup extends Command

  sealed trait Event extends JsonSerializable
  final case class GroupCreated(owner: String, members: Set[String]) extends Event
  final case class MemberAdded(userId: String) extends Event
  final case class MemberRemoved(userId: String) extends Event
  case object GroupClosed extends Event

  final case class State(owner: String, members: Set[String]) extends JsonSerializable

  def apply(groupId: String, persistenceId: PersistenceId): Behavior[OutgoingMessage | Command] =
    Behaviors.setup { context =>
      EventSourcedBehavior[OutgoingMessage | Command, Event, Option[State]](
        persistenceId = persistenceId,
        emptyState = None,
        commandHandler = (state, cmd) => handleCommand(context, state, cmd),
        eventHandler = handleEvent
      ).snapshotWhen {
        case (state, GroupCreated(_, _), sequence) => true
        case (state, GroupClosed, sequence) => true
        case (state, _, sequence) => false
      }.withRetention(RetentionCriteria.snapshotEvery(5, keepNSnapshots = 1))
  }

  private def handleEvent(state: Option[State], event: Event) = {
    state match {
      case Some(s) =>
        event match {
          case MemberAdded(userId) => Some(s.copy(members = s.members + userId))
          case MemberRemoved(userId) => Some(s.copy(members = s.members - userId))
          case GroupClosed => None
          case _ => state
        }
      case None =>
        event match {
          case GroupCreated(owner, members) => Some(State(owner, members))
          case _ => state
        }
    }
  }

  private def handleCommand(context: ActorContext[OutgoingMessage | Command],
                            state: Option[State],
                            cmd: OutgoingMessage | Command): Effect[Event, Option[State]] = {
    state match {
      case Some(s) =>
        cmd match {
          case MakeGroup(owner, members) => Effect.none
          case AddMember(member) => Effect.persist(MemberAdded(member))
          case RemoveMember(member) => Effect.persist(MemberRemoved(member))
          case CloseGroup => Effect.persist(GroupClosed).thenStop()
          case message: OutgoingMessage =>
            relayMessage(context, s, message)
            Effect.none
        }
      case None =>
        cmd match {
          case MakeGroup(owner, members) => Effect.persist(GroupCreated(owner, members))
          case _ => Effect.stop()
        }
    }
  }

  private def relayMessage(context: ActorContext[OutgoingMessage | Command], state: State, message: OutgoingMessage): Unit = {
    context.log.debug(s"Relaying message ${message.text} to ${state.members}")
    if (state.owner != message.from) {
      Exchange.shardRegion ! ShardingEnvelope(state.owner, message)
    }
    state.members.withFilter(_ != message.from).foreach { u =>
      Exchange.shardRegion ! ShardingEnvelope(u, message)
    }
  }
}
