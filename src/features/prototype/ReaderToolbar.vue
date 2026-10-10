<script setup lang="ts">
import { onMounted, onUnmounted, ref, useId } from 'vue'
import { RouterLink } from 'vue-router'
import { attachReaderToolbarGesture } from '@/services/readerToolbarGesture'
const props = defineProps<{ label: string; closeLabel: string; actions: { id: string; label: string; icon: string; to?: string; pressed?: boolean; disabled?: boolean }[] }>()
const emit = defineEmits<{ action: [id: string] }>()
const open = ref(false), root = ref<HTMLElement>(), toggle = ref<HTMLButtonElement>(), panelId = useId()
const paths: Record<string, string> = {
  tools: 'M4 6h16M4 12h16M4 18h16M8 4v4m8 2v4m-6 2v4',
  compare: 'M3 4h7v16H3zM14 4h7v16h-7zM5 8h3m-3 4h3m8-4h3m-3 4h3',
  bible: 'M3 5c3-1 6-1 9 1 3-2 6-2 9-1v14c-3-1-6-1-9 1-3-2-6-2-9-1zM12 6v14',
  search: 'M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14m5 12 6 6',
  commentary: 'M3 4h18v13H9l-6 4zM7 8h10M7 12h7',
  settings: 'M4 6h16M4 12h16M4 18h16M8 4v4m8 2v4m-6 2v4',
  night: 'M20 15a9 9 0 1 1-11-11 7 7 0 0 0 11 11',
  day: 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10M12 2v2m0 16v2M2 12h2m16 0h2M5 5l2 2m10 10 2 2M5 19l2-2M17 7l2-2',
  back: 'M20 12H4m7-7-7 7 7 7', forward: 'M4 12h16m-7-7 7 7-7 7',
  history: 'M3 11a9 9 0 1 1 2 7M3 4v7h7m2-5v6l4 2',
  place: 'M4 4h16v16H4zM4 9h16M9 4v16M14 9v11M4 14h16',
  favorite: 'm12 3 3 6 7 1-5 5 1 7-6-3-6 3 1-7-5-5 7-1z',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18m0 7v7m0-10v.2', close: 'm6 6 12 12M18 6 6 18',
}
let dispose: (() => void) | undefined
function outside(event: PointerEvent) { if (open.value && event.target instanceof Node && !root.value?.contains(event.target)) open.value = false }
function escape(event: KeyboardEvent) { if (open.value && event.key === 'Escape') { event.preventDefault(); close() } }
onMounted(() => {
  const app = root.value?.closest<HTMLElement>('.mobile-app'); if (app) dispose = attachReaderToolbarGesture(app, () => { open.value = true })
  document.addEventListener('pointerdown', outside); document.addEventListener('keydown', escape)
})
onUnmounted(() => { dispose?.(); document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape) })
function close() { open.value = false; toggle.value?.focus({ preventScroll: true }) }
function activate(id: string) { open.value = false; emit('action', id) }
</script>
<template>
  <div ref="root" class="reader-toolbar" data-no-reader-gesture @keydown.esc.stop="close">
    <button ref="toggle" class="toolbar-toggle" type="button" :aria-label="label" :title="label" :aria-expanded="open" :aria-controls="panelId" @click="open=!open">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="paths.tools" /></svg>
    </button>
    <nav v-if="open" :id="panelId" class="toolbar-panel" :aria-label="label">
      <component :is="action.to ? RouterLink : 'button'" v-for="action in props.actions" :key="action.id" :to="action.to" :type="action.to ? undefined : 'button'" :aria-label="action.label" :title="action.label" :aria-pressed="action.pressed" :disabled="action.disabled" class="toolbar-action" @click="activate(action.id)">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="paths[action.icon]" /></svg><span class="toolbar-tip" role="tooltip">{{ action.label }}</span>
      </component>
      <button class="toolbar-action" type="button" :aria-label="closeLabel" :title="closeLabel" @click="close"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="paths.close" /></svg><span class="toolbar-tip" role="tooltip">{{closeLabel}}</span></button>
    </nav>
  </div>
</template>
<style scoped>
.reader-toolbar{position:relative;display:flex;justify-content:flex-end;z-index:12}
.toolbar-toggle,.toolbar-action{display:flex;align-items:center;justify-content:center;position:relative;width:44px;height:44px;flex:0 0 44px;border:1px solid var(--line);border-radius:10px;background:var(--white);color:var(--ink);padding:10px;text-decoration:none}
svg{width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round}
.toolbar-panel{position:absolute;top:48px;right:0;display:grid;grid-template-columns:repeat(6,44px);gap:8px;padding:12px;background:var(--white);border:1px solid var(--line);border-radius:14px;box-shadow:0 8px 28px #0003;width:max-content;max-width:calc(100vw - 28px)}
.toolbar-action[aria-pressed=true]{background:var(--light-blue,#e5edf4);border-color:var(--brand-blue)}
.toolbar-action{position:static}
.toolbar-tip{display:none;position:absolute;top:calc(100% + 6px);left:0;right:0;z-index:1;background:var(--ink);color:var(--white);border-radius:6px;padding:6px 9px;font-size:12px;text-align:center;pointer-events:none}
.toolbar-action:hover .toolbar-tip,.toolbar-action:focus-visible .toolbar-tip{display:block}
@media(max-width:600px){.toolbar-panel{grid-template-columns:repeat(5,44px)}}
@media(max-width:370px){.toolbar-panel{grid-template-columns:repeat(4,44px)}}
</style>
