<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import { dictionaryMessages } from '@/i18n/dictionaries'
import { useI18n } from '@/i18n'
import { readCalendarState, writeCalendarState } from '@/offline/calendarMedia'
import { installedDictionaryImage } from '@/services/installedDictionaries'
const props = defineProps<{ url: string; title: string; version?: string | null; installed?: boolean; moduleCode?: string; mediaId?: number }>(), { language } = useI18n()
const text = computed(() => dictionaryMessages[language.value]), scale = ref(1), x = ref(0), y = ref(0), image = ref(props.url), frame = ref<HTMLElement>(), saved = ref(false), failed = ref(false)
let objectUrl = '', start: { x: number; y: number; px: number; py: number } | undefined
watch(() => props.url, async url => { scale.value = 1; x.value = y.value = 0; saved.value = props.installed ?? false; failed.value = false; if (objectUrl) URL.revokeObjectURL(objectUrl); image.value = url; if(props.installed)return; const blob = (props.moduleCode&&props.mediaId?await installedDictionaryImage(props.moduleCode,props.mediaId):undefined) ?? await readCalendarState<Blob>(`dictionary:image:${url}:${props.version}`).catch(() => undefined); if (blob && props.url === url) { objectUrl = URL.createObjectURL(blob); image.value = objectUrl; saved.value = true } }, { immediate: true })
async function save() { try { const response = await fetch(props.url); if (!response.ok || !/^image\/(png|jpeg|webp|gif)$/.test(response.headers.get('content-type')?.split(';')[0] ?? '')) throw new Error(); const blob = await response.blob(); if (blob.size > 20 * 1024 * 1024) throw new Error(); await writeCalendarState(`dictionary:image:${props.url}:${props.version}`, blob); saved.value = true } catch { failed.value = true } }
function down(e: PointerEvent) { frame.value?.setPointerCapture(e.pointerId); start = { x: e.clientX, y: e.clientY, px: x.value, py: y.value } }
function move(e: PointerEvent) { if (start) { x.value = start.px + e.clientX - start.x; y.value = start.py + e.clientY - start.y } }
function reset() { scale.value = 1; x.value = y.value = 0 }
onBeforeUnmount(() => { if (objectUrl) URL.revokeObjectURL(objectUrl) })
</script>
<template><section class="atlas"><nav><label>{{ text.zoom }} <input v-model.number="scale" type="range" min="1" max="8" step="0.1"></label><button @click="reset">{{ text.reset }}</button><button @click="frame?.requestFullscreen().catch(() => undefined)">{{ text.fullscreen }}</button><button :disabled="saved" @click="save">{{ saved ? text.saved : text.save }}</button></nav><p v-if="failed" role="alert">{{ text.imageError }}</p><div ref="frame" class="atlas-frame" tabindex="0" @pointerdown="down" @pointermove="move" @pointerup="start = undefined" @pointercancel="start = undefined" @keydown.left.prevent="x += 30" @keydown.right.prevent="x -= 30" @keydown.up.prevent="y += 30" @keydown.down.prevent="y -= 30" @wheel.prevent="scale = Math.max(1, Math.min(8, scale - $event.deltaY / 500))"><img :src="image" :alt="title" draggable="false" :style="{ transform: `translate(${x}px, ${y}px) scale(${scale})` }"></div></section></template>
<style scoped>.atlas nav{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.atlas-frame{height:55dvh;overflow:hidden;touch-action:none;background:#111;display:flex;align-items:center;justify-content:center}.atlas-frame:fullscreen{height:100vh}.atlas-frame img{max-width:100%;max-height:100%;object-fit:contain;transform-origin:center;user-select:none}</style>
