import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const packageFile = fileURLToPath(
  new URL('../ios/App/CapApp-SPM/Package.swift', import.meta.url),
)
const source = await readFile(packageFile, 'utf8')
const normalized = source
  .split(/(?<=\n)/u)
  .map((line) =>
    line.includes('.package(') && line.includes('path:')
      ? line.replaceAll('\\', '/')
      : line,
  )
  .join('')

if (normalized !== source) {
  await writeFile(packageFile, normalized)
  console.log('Normalized local Swift package paths for macOS.')
}
