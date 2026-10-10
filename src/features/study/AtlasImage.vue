<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import { dictionaryMessages } from '@/i18n/dictionaries'
import { useI18n } from '@/i18n'
import { readCalendarState, writeCalendarState } from '@/offline/calendarMedia'
import { installedDictionaryImage } from '@/services/installedDictionaries'
const props = defineProps<{ url: string; title: string; version?: string | null; installed?: boolean; moduleCode?: string; mediaId?: number }>(), { language, messages } = useI18n()
const text = computed(() => dictionaryMessages[language.value]), scale = ref(1), x = ref(0), y = ref(0), image = ref(''), frame = ref<HTMLElement>(), saved = ref(false), failed = ref(false), loading = ref(true), loadFailed = ref(false)
let objectUrl = '', generation = 0, start: { x: number; y: number; px: number; py: number } | undefined
function release() { if (objectUrl) URL.revokeObjectURL(objectUrl); objectUrl = '' }
async function load(useCache = true) {
  const request = ++generation, url = props.url
  reset(); release(); image.value = ''; loading.value = true; loadFailed.value = failed.value = false; saved.value = props.installed ?? false
  const blob = useCache && !props.installed ? await (async () => {
    try { return (props.moduleCode && props.mediaId ? await installedDictionaryImage(props.moduleCode, props.mediaId, props.version) : undefined) ?? await readCalendarState<Blob>(`dictionary:image:${url}:${props.version}`) } catch { return undefined }
  })() : undefined
  if (request !== generation) return
  if (blob) { objectUrl = URL.createObjectURL(blob); image.value = objectUrl; saved.value = true }
  else image.value = url
}
watch(() => [props.url, props.version, props.installed], () => { void load() }, { immediate: true })
function loaded(e: Event) { const element = e.target as HTMLImageElement; loading.value = false; loadFailed.value = !element.naturalWidth || !element.naturalHeight }
function error() {
  if (objectUrl && !props.installed) { void load(false); return }
  loading.value = false; loadFailed.value = true; saved.value = false
}
async function save() {
  try {
    const response = await fetch(props.url)
    if (!response.ok || !/^image\/(png|jpeg|webp|gif)$/.test(response.headers.get('content-type')?.split(';')[0] ?? '')) throw new Error()
    const blob = await response.blob()
    if (blob.size > 20 * 1024 * 1024) throw new Error()
    const bitmap = await createImageBitmap(blob); bitmap.close()
    await writeCalendarState(`dictionary:image:${props.url}:${props.version}`, blob); saved.value = true
  } catch { failed.value = true }
}
function down(e: PointerEvent) { if (e.button !== 0 || loading.value || loadFailed.value) return; frame.value?.setPointerCapture(e.pointerId); start = { x: e.clientX, y: e.clientY, px: x.value, py: y.value } }
function move(e: PointerEvent) { if (start) { x.value = start.px + e.clientX - start.x; y.value = start.py + e.clientY - start.y } }
function reset() { scale.value = 1; x.value = y.value = 0; start = undefined }
onBeforeUnmount(() => { generation++; release() })
</script>
<template><section class="atlas"><nav><label>{{ text.zoom }} <input v-model.number="scale" type="range" min="1" max="8" step="0.1"></label><button @click="reset">{{ text.reset }}</button><button @click="frame?.requestFullscreen().catch(() => undefined)">{{ text.fullscreen }}</button><button :disabled="saved || loading || loadFailed" @click="save">{{ saved ? text.saved : text.save }}</button></nav><p v-if="failed" role="alert">{{ text.imageError }}</p><div ref="frame" class="atlas-frame" tabindex="0" @pointerdown="down" @pointermove="move" @pointerup="start = undefined" @pointercancel="start = undefined" @lostpointercapture="start = undefined" @keydown.left.prevent="x += 30" @keydown.right.prevent="x -= 30" @keydown.up.prevent="y += 30" @keydown.down.prevent="y -= 30" @wheel.prevent="scale = Math.max(1, Math.min(8, scale - $event.deltaY / 500))"><p v-if="loading" role="status">{{ messages.loading }}</p><p v-if="loadFailed" role="alert">{{ text.imageLoadError }} <button @click="load(false)">{{ text.retry }}</button></p><img v-if="image" v-show="!loading && !loadFailed" :src="image" :alt="title" draggable="false" :style="{ transform: `translate(${x}px, ${y}px) scale(${scale})` }" @load="loaded" @error="error"></div></section></template>
<style scoped>.atlas nav{display:flex;align-items:center;gap:6px;flex-wrap:wrap;margin:8px 0;font-size:14px}.atlas nav button{padding:6px 10px;min-height:36px}.atlas nav label{display:flex;align-items:center;gap:6px}.atlas nav input{width:120px}.atlas-frame{height:55dvh;overflow:hidden;touch-action:none;background:#111;display:flex;align-items:center;justify-content:center;color:white}.atlas-frame:fullscreen{height:100dvh;width:100vw}.atlas-frame img{max-width:100%;max-height:100%;object-fit:contain;transform-origin:center;user-select:none}.atlas-frame p{padding:12px}</style>
