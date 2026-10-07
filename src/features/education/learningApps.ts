export type LearningApp = {
  id: 'azbuka'
  icon: string
  launch:
    | { type: 'standalone'; href: string }
    | { type: 'plugin'; route: string }
    | { type: 'bot'; href: string }
}

export const learningApps: LearningApp[] = [
  {
    id: 'azbuka',
    icon: '/app-icons/library.png',
    launch: { type: 'standalone', href: 'https://azbuka.bible-desktop.com/' },
  },
]
