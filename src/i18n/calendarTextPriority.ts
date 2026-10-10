import type {InterfaceLanguage} from './locale'
export const calendarTextPriorityMessages:Record<InterfaceLanguage,{automatic:string;missing:string}>={
 ru:{automatic:'Редакция выбирается отдельно для каждого текста по языку календаря.',missing:'Для этого текста подходящая опубликованная редакция недоступна.'},
 uk:{automatic:'Редакція вибирається окремо для кожного тексту за мовою календаря.',missing:'Для цього тексту відповідна опублікована редакція недоступна.'},
 de:{automatic:'Die Ausgabe wird für jeden Text anhand der Kalendersprache ausgewählt.',missing:'Für diesen Text ist keine passende veröffentlichte Ausgabe verfügbar.'},
 en:{automatic:'Each text uses an edition selected for the calendar language.',missing:'No suitable published edition is available for this text.'},
}
