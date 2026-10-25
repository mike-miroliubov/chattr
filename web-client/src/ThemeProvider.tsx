import { useSyncExternalStore, type ReactNode } from 'react'
import { App as AntApp, ConfigProvider, theme } from 'antd'

const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')

function subscribe(onChange: () => void) {
  darkQuery.addEventListener('change', onChange)
  return () => darkQuery.removeEventListener('change', onChange)
}

// Follows the OS light/dark setting
export function ThemeProvider({ children }: { children: ReactNode }) {
  const prefersDark = useSyncExternalStore(subscribe, () => darkQuery.matches)

  return (
    <ConfigProvider theme={{ algorithm: prefersDark ? theme.darkAlgorithm : theme.defaultAlgorithm }}>
      <AntApp>{children}</AntApp>
    </ConfigProvider>
  )
}
