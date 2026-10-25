// Chat ids, as messenger-api derives them: group chats are "g#<name>",
// direct chats are both user names sorted and joined with "#"

export function chatIdFor(userName: string, recipient: string): string {
  return recipient.startsWith('g#') ? recipient : [userName, recipient].sort().join('#')
}

// Whom messages in a chat are addressed to: the other user, or the group itself
export function recipientOf(chatId: string, userName: string): string {
  if (chatId.startsWith('g#')) {
    return chatId
  }
  return chatId.split('#').find((name) => name !== userName) ?? userName
}
