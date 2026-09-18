import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.bibledesktop.myapp',
  appName: 'My Bible App',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
  },
}

export default config
