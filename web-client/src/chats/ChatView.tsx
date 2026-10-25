import { ArrowLeftOutlined, SendOutlined } from '@ant-design/icons'
import { useQueryClient } from '@tanstack/react-query'
import { Alert, Avatar, Button, Empty, Flex, Input, Spin, Space, Typography, theme } from 'antd'
import { useEffect, useRef, useState } from 'react'
import type { MessengerClient } from '../messenger/MessengerClient.ts'
import { chatIdFor } from './chatId.ts'
import { formatTime } from './format.ts'
import { addMessageToCache, useMessages } from './queries.ts'

interface ChatViewProps {
  userName: string
  recipient: string
  messenger: MessengerClient
  // Shown on narrow screens, where the chat replaces the list
  onBack?: () => void
}

export function ChatView({ userName, recipient, messenger, onBack }: ChatViewProps) {
  const chatId = chatIdFor(userName, recipient)
  const { data: messages, isPending, error, refetch } = useMessages(chatId)
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)
  const { token } = theme.useToken()

  // Keep the latest message in view
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages])

  const send = () => {
    if (!text.trim()) {
      return
    }
    const id = crypto.randomUUID()
    messenger.send({ id, to: recipient, text })
    // The server doesn't echo messages back to the sender
    addMessageToCache(queryClient, userName, chatId, { id, from: userName, text, receivedAt: Date.now() })
    setText('')
  }

  return (
    <Flex vertical style={{ height: '100%', minHeight: 0 }}>
      <Flex
        align="center"
        gap="small"
        style={{ padding: '12px 16px', borderBottom: `1px solid ${token.colorBorderSecondary}` }}
      >
        {onBack && <Button type="text" icon={<ArrowLeftOutlined />} onClick={onBack} aria-label="Back to chats" />}
        <Avatar>{recipient.charAt(0).toUpperCase()}</Avatar>
        <Typography.Text strong ellipsis>{recipient}</Typography.Text>
      </Flex>

      <Flex vertical gap="small" style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
        {isPending ? (
          <Spin style={{ margin: 'auto' }} />
        ) : error ? (
          <Alert
            type="error"
            showIcon
            title="Couldn't load messages"
            description={error.message}
            action={<Button size="small" onClick={() => refetch()}>Retry</Button>}
          />
        ) : messages.length === 0 ? (
          <Empty style={{ margin: 'auto' }} image={Empty.PRESENTED_IMAGE_SIMPLE} description="No messages yet" />
        ) : (
          messages.map((message) => {
            const mine = message.from === userName
            return (
              <div
                key={message.id}
                style={{
                  alignSelf: mine ? 'flex-end' : 'flex-start',
                  maxWidth: '75%',
                  padding: '6px 12px',
                  borderRadius: token.borderRadiusLG,
                  background: mine ? token.colorPrimary : token.colorFillSecondary,
                  color: mine ? token.colorTextLightSolid : token.colorText,
                  whiteSpace: 'pre-wrap',
                  overflowWrap: 'anywhere',
                }}
              >
                {message.text}
                <div style={{ fontSize: token.fontSizeSM, opacity: 0.65, textAlign: 'right' }}>
                  {formatTime(message.receivedAt)}
                </div>
              </div>
            )
          })
        )}
        <div ref={bottomRef} />
      </Flex>

      <Space.Compact style={{ padding: 16, borderTop: `1px solid ${token.colorBorderSecondary}` }}>
        <Input
          placeholder="Message"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onPressEnter={send}
          autoFocus
        />
        <Button type="primary" icon={<SendOutlined />} onClick={send} disabled={!text.trim()} aria-label="Send" />
      </Space.Compact>
    </Flex>
  )
}
