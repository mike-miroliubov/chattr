const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })
const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' })

// Time for today's timestamps, the date otherwise
export function formatShortTime(timestamp: number): string {
  const date = new Date(timestamp)
  const isToday = date.toDateString() === new Date().toDateString()
  return isToday ? timeFormat.format(date) : dateFormat.format(date)
}

export function formatTime(timestamp: number): string {
  return timeFormat.format(new Date(timestamp))
}
