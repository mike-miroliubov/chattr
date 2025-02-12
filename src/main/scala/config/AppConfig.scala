package org.chats
package config

import service.ChatServiceImpl

import org.chats.repository.InMemoryRepository

val repository = InMemoryRepository()
val chatService = ChatServiceImpl(repository, repository)