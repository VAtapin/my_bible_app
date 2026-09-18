import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.bibledesktop.myapp',
  appName: 'Bible Desktop',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
  },
  plugins: {
    LocalNotifications: {
      iconColor: '#4A6B8A',
      presentationOptions: ['sound', 'banner', 'list'],
    },
  },
}

export default config
