<script setup lang="ts">
import { ref } from 'vue'
const props=defineProps<{text:string;enabled:boolean}>()
const emit=defineEmits<{selection:[start:number,end:number]}>()
const root=ref<HTMLElement>()
let origin:{x:number;y:number;offset:number}|undefined,dragging=false,cancelled=false
function offsetAt(x:number,y:number):number|undefined{
 const doc=document as Document&{caretPositionFromPoint?:(x:number,y:number)=>{offsetNode:Node;offset:number}|null;caretRangeFromPoint?:(x:number,y:number)=>Range|null}
 const position=doc.caretPositionFromPoint?.(x,y),range=position?undefined:doc.caretRangeFromPoint?.(x,y),node=position?.offsetNode??range?.startContainer,offset=position?.offset??range?.startOffset
 if(!node||offset===undefined||!root.value?.contains(node))return
 const before=document.createRange();before.selectNodeContents(root.value);before.setEnd(node,offset);return before.toString().length
}
function start(event:PointerEvent){if(!props.enabled)return;const offset=offsetAt(event.clientX,event.clientY);if(offset!==undefined){origin={x:event.clientX,y:event.clientY,offset};dragging=false;cancelled=false}}
function move(event:PointerEvent){if(!origin||cancelled)return;const x=event.clientX-origin.x,y=event.clientY-origin.y;if(!dragging){if(Math.abs(y)>12&&Math.abs(y)>Math.abs(x)){cancelled=true;return}if(Math.abs(x)<12||Math.abs(x)<=Math.abs(y))return;dragging=true;root.value?.setPointerCapture(event.pointerId)}if(dragging){event.preventDefault();const at=offsetAt(event.clientX,event.clientY);if(at===undefined)return;emit('selection',Math.min(origin.offset,at),Math.max(origin.offset,at));const node=root.value?.firstChild;if(node?.nodeType===Node.TEXT_NODE){const range=document.createRange();range.setStart(node,Math.min(origin.offset,at));range.setEnd(node,Math.max(origin.offset,at));window.getSelection()?.removeAllRanges();window.getSelection()?.addRange(range)}}}
function end(){origin=undefined;dragging=false;const selection=window.getSelection();if(!selection?.rangeCount)return;const range=selection.getRangeAt(0),element=root.value;if(!element?.contains(range.startContainer)||!element.contains(range.endContainer)||range.collapsed)return;const before=range.cloneRange();before.selectNodeContents(element);before.setEnd(range.startContainer,range.startOffset);emit('selection',before.toString().length,before.toString().length+range.toString().length)}
</script>
<template><p ref="root" class="word-mark-text" @pointerdown="start" @pointermove="move" @pointerup="end" @pointercancel="origin=undefined;dragging=false" @keyup="end">{{text}}</p></template>
<style scoped>.word-mark-text{white-space:pre-wrap;line-height:1.7;user-select:text;touch-action:pan-y;padding:12px;background:var(--white)}</style>
