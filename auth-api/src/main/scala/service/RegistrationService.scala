package org.chats
package service

import repository.UserRepository

import com.fasterxml.uuid.Generators
import org.chats.exception.{AuthError, InternalAuthError}
import org.chats.model.User
import zio.{IO, ZIO}

import java.security.SecureRandom
import java.time.{Clock, Instant, LocalDateTime, ZoneOffset, ZonedDateTime}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val idGenerator = Generators.timeBasedEpochRandomGenerator()
private val ITERATIONS = 600000
private val KEY_LENGTH = 256
private val SALT_LENGTH = 16

class RegistrationService(private val userRepository: UserRepository) {
  def register(username: String, password: String): IO[AuthError, User] = {
    val salt = makeSalt()
    val hashedPassword = hashPassword(password, salt)

    val user = User(idGenerator.generate().toString, username, salt ++ hashedPassword, Instant.now(Clock.systemDefaultZone()))
    userRepository.create(user).as(user)
  }
}

private def hashPassword(password: String, salt: Array[Byte]): Array[Byte] = {
  val spec = new PBEKeySpec(password.toCharArray, salt, ITERATIONS, KEY_LENGTH)
  val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
  factory.generateSecret(spec).getEncoded
}

private def makeSalt(): Array[Byte] = {
  val random = SecureRandom.getInstance("DRBG")
  val salt: Array[Byte] = Array.ofDim(SALT_LENGTH)
  random.nextBytes(salt)
  salt
}
