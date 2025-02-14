package org.chats
package config

import service.ChatServiceImpl

import org.chats.repository.InMemoryRepository

lazy val repository = InMemoryRepository()
lazy val chatService = ChatServiceImpl(repository, repository)