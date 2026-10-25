import { UserOutlined } from '@ant-design/icons'
import { Button, Card, Flex, Form, Input, Typography } from 'antd'
import { login } from '../session/session.ts'

interface LoginForm {
  userName: string
}

export function LoginView() {
  const handleFinish = ({ userName }: LoginForm) => login(userName.trim())

  return (
    <Flex justify="center" style={{ paddingTop: '15vh' }}>
      <Card style={{ width: 'min(360px, calc(100% - 32px))' }}>
        <Typography.Title level={3} style={{ marginTop: 0 }}>Chattr</Typography.Title>
        <Typography.Paragraph type="secondary">Please log in</Typography.Paragraph>
        <Form<LoginForm> layout="vertical" onFinish={handleFinish} requiredMark={false}>
          <Form.Item
            label="Login"
            name="userName"
            rules={[{ required: true, whitespace: true, message: 'Enter your user name' }]}
          >
            <Input prefix={<UserOutlined />} autoFocus autoComplete="username" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block>Log in</Button>
        </Form>
      </Card>
    </Flex>
  )
}
