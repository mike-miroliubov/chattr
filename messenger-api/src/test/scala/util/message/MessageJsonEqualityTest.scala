package org.chats
package util.message

import com.fasterxml.uuid.Generators
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MessageJsonEqualityTest extends AnyFlatSpec with Matchers {

  private val idGenerator = Generators.timeBasedEpochRandomGenerator()

  "Message JSONs" should "be equal with the exception of ids" in {
    // given
    val message1 = messageJson(chatId = "bar#foo", from = "foo", id = idGenerator.generate().toString, text = "hi")
    val message2 = messageJson(chatId = "bar#foo", from = "foo", id = idGenerator.generate().toString, text = "hi")

    message1.asJsObject.fields("id") should not be message2.asJsObject.fields("id")

    // when-then
    message1 should equal (message2)
  }
}
