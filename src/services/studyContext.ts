import type {BibleChapter} from '@/api/contracts'

/** Explicit verse study stays on that verse when continuous reading reports another chapter. */
export function studyContext(selected:BibleChapter|undefined,verse:number|undefined,visible:BibleChapter|undefined,initial:BibleChapter|undefined,first:number|undefined,last:number|undefined){
 return selected ? {chapter:selected,first:verse,last:verse} : {chapter:visible??initial,first,last}
}
