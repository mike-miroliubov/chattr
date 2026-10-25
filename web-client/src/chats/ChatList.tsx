import { SearchOutlined } from '@ant-design/icons'
import { Alert, Avatar, Button, Empty, Flex, Input, Skeleton, Typography, theme } from 'antd'
import { useState } from 'react'
import { recipientOf } from './chatId.ts'
import type { Chat } from './dto.ts'
import { formatShortTime } from './format.ts'
import { useChats } from './queries.ts'
import './ChatList.css'

interface ChatListProps {
  userName: string
  selectedChatId: string | null
  onSelect: (recipient: string) => void
}

export function ChatList({ userName, selectedChatId, onSelect }: ChatListProps) {
  const { data: chats, isPending, error, refetch } = useChats(userName)
  const [search, setSearch] = useState('')

  // Same as the CLI: filter by the beginning of the other user's name
  const prefix = search.trim().toLowerCase()
  const rows = (chats ?? [])
    .map((chat) => ({ chat, recipient: recipientOf(chat.id, userName) }))
    .filter(({ recipient }) => recipient.toLowerCase().startsWith(prefix))

  let content
  if (error) {
    content = (
      <Alert
        type="error"
        showIcon
        style={{ margin: '0 12px' }}
        title="Couldn't load chats"
        description={error.message}
        action={<Button size="small" onClick={() => refetch()}>Retry</Button>}
      />
    )
  } else if (isPending) {
    content = <Skeleton active avatar paragraph={{ rows: 1 }} style={{ padding: 12 }} />
  } else if (rows.length === 0) {
    content = <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={prefix ? 'No matching chats' : 'No chats yet'} />
  } else {
    content = (
      <ul className="chat-list">
        {rows.map(({ chat, recipient }) => (
          <li key={chat.id}>
            <ChatRow
              chat={chat}
              recipient={recipient}
              userName={userName}
              selected={chat.id === selectedChatId}
              onClick={() => onSelect(recipient)}
            />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <Flex vertical style={{ flex: 1, minHeight: 0 }}>
      <div style={{ padding: '0 12px 12px' }}>
        <Input
          prefix={<SearchOutlined />}
          placeholder="Search"
          allowClear
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      <div style={{ flex: 1, overflowY: 'auto' }}>{content}</div>
    </Flex>
  )
}

interface ChatRowProps {
  chat: Chat
  recipient: string
  userName: string
  selected: boolean
  onClick: () => void
}

function ChatRow({ chat, recipient, userName, selected, onClick }: ChatRowProps) {
  const { token } = theme.useToken()

  return (
    <button
      type="button"
      className="chat-list-row"
      aria-current={selected || undefined}
      onClick={onClick}
      style={{
        // Theme-dependent colors, read by ChatList.css
        ['--row-hover' as string]: token.controlItemBgHover,
        ['--row-selected' as string]: token.controlItemBgActive,
        ['--row-focus' as string]: token.colorPrimaryBorder,
      }}
    >
      <Avatar>{recipient.charAt(0).toUpperCase()}</Avatar>
      <Flex vertical style={{ flex: 1, minWidth: 0 }}>
        <Flex justify="space-between" gap="small">
          <Typography.Text strong ellipsis>{recipient}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM, flexShrink: 0 }}>
            {formatShortTime(chat.lastMessageAt)}
          </Typography.Text>
        </Flex>
        <Typography.Text type="secondary" ellipsis>
          {chat.fromUserId === userName ? `You: ${chat.lastText}` : chat.lastText}
        </Typography.Text>
      </Flex>
    </button>
  )
}
