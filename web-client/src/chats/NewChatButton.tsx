import { EditOutlined } from '@ant-design/icons'
import { Button, Form, Input, Modal } from 'antd'
import { useState } from 'react'

interface NewChatForm {
  recipient: string
}

export function NewChatButton({ onStart }: { onStart: (recipient: string) => void }) {
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm<NewChatForm>()

  const handleFinish = ({ recipient }: NewChatForm) => {
    setOpen(false)
    onStart(recipient.trim())
  }

  return (
    <>
      <Button type="primary" icon={<EditOutlined />} onClick={() => setOpen(true)}>New chat</Button>
      <Modal
        title="New chat"
        open={open}
        okText="Start chat"
        onOk={() => form.submit()}
        onCancel={() => setOpen(false)}
        afterClose={() => form.resetFields()}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={handleFinish} requiredMark={false}>
          <Form.Item
            label="User name"
            name="recipient"
            rules={[{ required: true, whitespace: true, message: 'Enter whom to chat with' }]}
          >
            <Input autoFocus />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
