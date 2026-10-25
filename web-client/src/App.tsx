import { LogoutOutlined } from '@ant-design/icons'
import { Badge, Button, Flex, Layout, Typography, theme, type BadgeProps } from 'antd'
import { LoginView } from './login/LoginView.tsx'
import type { ConnectionStatus } from './messenger/MessengerClient.ts'
import { logout, useSession } from './session/session.ts'

const connectionBadges: Record<ConnectionStatus, { status: BadgeProps['status'], text: string }> = {
  connecting: { status: 'processing', text: 'Connecting' },
  open: { status: 'success', text: 'Connected' },
  reconnecting: { status: 'warning', text: 'Reconnecting' },
  failed: { status: 'error', text: 'Disconnected' },
  closed: { status: 'default', text: 'Offline' },
}

function App() {
  const { userName, connectionStatus } = useSession()
  const { token } = theme.useToken()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {userName ? (
        <>
          <Layout.Header style={{ background: token.colorBgContainer, paddingInline: 16 }}>
            <Flex align="center" justify="space-between" style={{ height: '100%' }}>
              <Typography.Title level={4} style={{ margin: 0 }}>Chattr</Typography.Title>
              <Flex align="center" gap="middle">
                <Badge {...connectionBadges[connectionStatus]} />
                <Typography.Text strong>{userName}</Typography.Text>
                <Button icon={<LogoutOutlined />} onClick={logout}>Log out</Button>
              </Flex>
            </Flex>
          </Layout.Header>
          {/* Placeholder until the chat list is in place */}
          <Layout.Content style={{ padding: 16 }} />
        </>
      ) : (
        <LoginView />
      )}
    </Layout>
  )
}

export default App
