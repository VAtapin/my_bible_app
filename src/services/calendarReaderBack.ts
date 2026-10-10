let serial=0
/** An extra same-URL entry closes local reading without changing the selected calendar date. */
export function calendarReaderBack(close:()=>void,target:Pick<Window,'history'|'location'|'addEventListener'|'removeEventListener'>=window){
 const token=`calendar-reader-${Date.now()}-${++serial}`,key='calendarLocalReader'
 let active=true
 const state=target.history.state&&typeof target.history.state==='object'?target.history.state:{}
 target.history.pushState({...state,[key]:token},'',target.location.href)
 function changed(){if(active&&target.history.state?.[key]!==token){active=false;close()}}
 target.addEventListener('popstate',changed)
 return {
   close(){if(!active)return;if(target.history.state?.[key]===token)target.history.back();else changed()},
   dispose(){target.removeEventListener('popstate',changed);if(active&&target.history.state?.[key]===token)target.history.back();active=false},
 }
}
