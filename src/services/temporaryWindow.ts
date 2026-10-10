import type{ReaderWindows}from'./readerWindows'
export interface WindowSnapshot {state:ReaderWindows;mode:string}
export function snapshotWindows(state:ReaderWindows,mode:string):WindowSnapshot {
 return {state:{...state,places:[{...state.places[0]},{...state.places[1]}],open:[...state.open]},mode}
}
