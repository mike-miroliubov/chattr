import { Empty, Flex, Grid, Layout, Typography, theme } from 'antd'
import { useState } from 'react'
import type { MessengerClient } from '../messenger/MessengerClient.ts'
import { chatIdFor } from './chatId.ts'
import { ChatList } from './ChatList.tsx'
import { ChatView } from './ChatView.tsx'
import { NewChatButton } from './NewChatButton.tsx'
import { useLiveUpdates } from './useLiveUpdates.ts'

interface ChatsPageProps {
  userName: string
  messenger: MessengerClient
}

// Chat list on the left, the open chat on the right. On narrow screens only one of them is shown.
export function ChatsPage({ userName, messenger }: ChatsPageProps) {
  const [recipient, setRecipient] = useState<string | null>(null)
  const { md: wide } = Grid.useBreakpoint()
  const { token } = theme.useToken()
  useLiveUpdates(userName, messenger)

  const showList = wide || !recipient
  const showChat = wide || !!recipient

  return (
    <Layout style={{ flex: 1, minHeight: 0 }}>
      {showList && (
        <Layout.Sider
          width={wide ? 320 : '100%'}
          style={{ background: token.colorBgContainer, borderRight: `1px solid ${token.colorBorderSecondary}` }}
        >
          <Flex vertical style={{ height: '100%' }}>
            <Flex align="center" justify="space-between" style={{ padding: 12 }}>
              <Typography.Title level={5} style={{ margin: 0 }}>Chats</Typography.Title>
              <NewChatButton onStart={setRecipient} />
            </Flex>
            <ChatList
              userName={userName}
              selectedChatId={recipient && chatIdFor(userName, recipient)}
              onSelect={setRecipient}
            />
          </Flex>
        </Layout.Sider>
      )}
      {showChat && (
        <Layout.Content style={{ background: token.colorBgContainer, minWidth: 0 }}>
          {recipient ? (
            <ChatView
              // A fresh view per chat, so the draft and scroll position don't carry over
              key={recipient}
              userName={userName}
              recipient={recipient}
              messenger={messenger}
              onBack={wide ? undefined : () => setRecipient(null)}
            />
          ) : (
            <Flex align="center" justify="center" style={{ height: '100%' }}>
              <Empty description="Select a chat or start a new one" />
            </Flex>
          )}
        </Layout.Content>
      )}
    </Layout>
  )
}
