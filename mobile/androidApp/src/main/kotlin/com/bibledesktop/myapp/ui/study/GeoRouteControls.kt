package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal data class GeoRouteLabels(val routes:String,val none:String,val forPlace:String,val stops:String,val schematic:String)
internal fun geoRouteLabels(language:String)=when(language){
 "ru"->GeoRouteLabels("Маршруты","Без маршрута","Маршруты этого места","Остановки","Схема выбранных библейских остановок. Линии не обозначают древние дороги; расположение зависит от выбранного варианта.")
 "de"->GeoRouteLabels("Routen","Ohne Route","Routen dieses Ortes","Stationen","Schema ausgewählter biblischer Stationen. Linien sind keine antiken Straßen; Standorte hängen von gewählten Varianten ab.")
 "uk"->GeoRouteLabels("Маршрути","Без маршруту","Маршрути цього місця","Зупинки","Схема вибраних біблійних зупинок. Лінії не позначають давні дороги; розташування залежить від вибраного варіанта.")
 else->GeoRouteLabels("Routes","No route","Routes through this place","Stops","Selected biblical stops. Lines do not represent ancient roads; locations depend on the selected alternatives.")
}
internal fun geoRouteName(language:String,id:String):String {
 val names=when(language){
  "ru"->listOf("Первое путешествие Павла","Авраам: Бытие 11–13","Павел: Македония и Греция","Иисус: события в Галилее")
  "de"->listOf("Erste Reise des Paulus","Abraham: Genesis 11–13","Paulus: Mazedonien und Griechenland","Jesus: Ereignisse in Galiläa")
  "uk"->listOf("Перша подорож Павла","Авраам: Буття 11–13","Павло: Македонія та Греція","Ісус: події в Галилеї")
  else->listOf("Paul’s first journey","Abraham: Genesis 11–13","Paul: Macedonia and Greece","Jesus: events in Galilee")
 }
 return listOf("paul-first","abraham","paul-macedonia","jesus-galilee").indexOf(id).takeIf{it>=0}?.let{names[it]}?:id
}
@Composable internal fun GeoRoutePicker(routes:List<GeoRoute>,selected:String?,language:String,onSelect:(String?)->Unit){
 var expanded by remember{mutableStateOf(false)}
 val text=geoRouteLabels(language)
 Column {
  TextButton(onClick={expanded=true},modifier=Modifier.testTag("geo-route-picker")){Text("${text.routes}: ${selected?.let{geoRouteName(language,it)}?:text.none}")}
  DropdownMenu(expanded,onDismissRequest={expanded=false}){
   DropdownMenuItem(text={Text(text.none)},onClick={onSelect(null);expanded=false})
   routes.forEach{route->DropdownMenuItem(text={Text(geoRouteName(language,route.id))},onClick={onSelect(route.id);expanded=false})}
  }
 }
}
