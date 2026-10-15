package com.bibledesktop.myapp.ui.study

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun GeoAtlasScreen(language:String,onBack:()->Unit,references:List<String> = emptyList(),initialQuery:String="") {
 val context=LocalContext.current;val text=geoTexts(language)
 var data by remember{mutableStateOf<GeoData?>(null)};var failed by remember{mutableStateOf(false)};var attempt by remember{mutableIntStateOf(0)}
 var query by remember(initialQuery){mutableStateOf(initialQuery)};var filtered by remember(references){mutableStateOf(false)};var selected by remember{mutableStateOf<GeoPlace?>(null)};var variants by remember{mutableStateOf<Map<String,String>>(emptyMap())};var routeId by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(attempt){failed=false;try{data=withContext(Dispatchers.IO){context.assets.open("data/bible-geo.json").bufferedReader().use{parseGeoData(it.readText())}}}catch(_:Exception){failed=true}}
 val route=data?.routes?.find { it.id==routeId }
 val detailState=rememberLazyListState()
 var expandedStops by remember(routeId){mutableStateOf(false)}
 LaunchedEffect(routeId,selected?.id){detailState.animateScrollToItem(0)}
 val available=remember(data,route,query,filtered,references){data?.let{atlas->geographicPlaces(if(route==null)atlas else atlas.copy(places=geoRoutePlaces(atlas,route)),query,if(filtered)references else emptyList())}.orEmpty()}
 Column(Modifier.fillMaxSize().padding(12.dp)){
  Row{TextButton(onClick=onBack){Text(text.back)};Text(text.title,style=MaterialTheme.typography.titleLarge)}
  OutlinedTextField(query,{query=it;selected=null},label={Text(text.search)},modifier=Modifier.fillMaxWidth(),singleLine=true)
  if(references.isNotEmpty()){Row{Checkbox(filtered,{filtered=it;selected=null});Text(text.context)}}
  if(failed)Row{Text(text.error);TextButton(onClick={attempt++}){Text(text.retry)}}else if(data==null)CircularProgressIndicator()
  data?.let{atlas->
   GeoRoutePicker(atlas.routes,routeId,language){routeId=it;selected=null}
   GeoMap(atlas,available,selected,variants,route,text,{place,location->selected=place;variants=variants+(place.id to location.id)},Modifier.fillMaxWidth().height(280.dp),language)
   LazyColumn(Modifier.weight(1f).testTag("geo-atlas-details"),state=detailState,verticalArrangement=Arrangement.spacedBy(8.dp)){
    selected?.let { place ->
     val related=geographicRoutes(atlas,place.id)
     if(related.isNotEmpty()){
      item(key="selected-routes"){Text(geoRouteLabels(language).forPlace)}
      items(related,key={"selected-route:${it.id}"}){r->TextButton(onClick={routeId=r.id}){Text(geoRouteName(language,r.id))}}
     }
    }
    item{Column{Text(text.uncertain);Text(text.names)}}
    route?.let { active ->
     item{Column{Text(geoRouteLabels(language).schematic);Text(active.source)}}
     item(key="route-stops"){TextButton(onClick={expandedStops=!expandedStops},modifier=Modifier.testTag("geo-route-stops")){Text("${geoRouteLabels(language).stops} (${active.stops.size})")}}
     if(expandedStops)itemsIndexed(active.stops,key={index,_->"stop:${active.id}:$index"}){index,stop->
      atlas.places.find{it.id==stop.placeId}?.let{place->TextButton(onClick={selected=place;place.locations.firstOrNull()?.let{variants=variants+(place.id to it.id)}}){Text("${index+1}. ${place.name} · ${stop.osis}")}}
     }
    }
    selected?.let{place->item{Text(place.name,style=MaterialTheme.typography.titleLarge);Text(text.variants)};items(place.locations,key={"variant:${it.id}"}){location->OutlinedButton(onClick={variants=variants+(place.id to location.id)}){Text("${location.name.ifBlank{place.name}} · ${location.lat}, ${location.lon} · ${location.type}\n${location.score?.let{"${text.score}: $it"}?:text.unknown}")}};item{Text("${text.refs} (${place.verses.size}): ${place.verses.joinToString(" · ")}")}}
    if(available.isEmpty())item{Text(text.empty)}
    items(available,key={"place:${it.id}"}){place->TextButton(onClick={selected=place;place.locations.firstOrNull()?.let{variants=variants+(place.id to it.id)}}){Text("${place.name} · ${place.locations.size}\n${place.aliases.joinToString(" · ")}")}}
   }
  }
 }
}

@Composable private fun GeoMap(data:GeoData,places:List<GeoPlace>,selected:GeoPlace?,variants:Map<String,String>,activeRoute:GeoRoute?,text:GeoTexts,onSelect:(GeoPlace,GeoLocation)->Unit,modifier:Modifier,language:String) {
 var zoom by remember{mutableFloatStateOf(1f)};var pan by remember{mutableStateOf(Offset.Zero)};var fullscreen by remember{mutableStateOf(false)}
 val paths=remember(data){data.land.map{ring->Path().apply{ring.firstOrNull()?.let{moveTo(it.x,it.y)};ring.drop(1).forEach{lineTo(it.x,it.y)};close()}}}
 val pins=remember(places){places.flatMap{place->place.locations.map{Triple(place,it,projectGeo(it.lon,it.lat))}}}
 val route=remember(data,activeRoute,variants){activeRoute?.let{geoRoutePoints(data,it,variants)}.orEmpty()}
 LaunchedEffect(places){if(pins.isNotEmpty()){val left=pins.minOf{it.third.x};val right=pins.maxOf{it.third.x};val top=pins.minOf{it.third.y};val bottom=pins.maxOf{it.third.y};pan=Offset(500f-(left+right)/2,250f-(top+bottom)/2);zoom=minOf(32f,maxOf(1f,minOf(800f/maxOf(25f,right-left),400f/maxOf(12.5f,bottom-top))))}else{pan=Offset.Zero;zoom=1f}}
 LaunchedEffect(selected?.id,variants[selected?.id]){selected?.let{place->(place.locations.find{it.id==variants[place.id]}?:place.locations.firstOrNull())?.let{zoom=maxOf(zoom,8f);pan=Offset(500f,250f)-projectGeo(it.lon,it.lat)}}}
 @Composable fun map(area:Modifier,tag:String){
  Canvas(area.clipToBounds().testTag(tag).pointerInput(Unit){detectTransformGestures{_,delta,factor,_->pan+=delta/(minOf(size.width/1000f,size.height/500f)*zoom);zoom=(zoom*factor).coerceIn(1f,64f)}}.pointerInput(pins,zoom,pan){detectTapGestures{point->val base=minOf(size.width/1000f,size.height/500f);val origin=Offset(size.width/2f,size.height/2f);val selectedPin=pins.minByOrNull{(_,_,p)->((p+pan-Offset(500f,250f))*base*zoom+origin-point).getDistance()};selectedPin?.let{(place,loc,p)->if(((p+pan-Offset(500f,250f))*base*zoom+origin-point).getDistance()<24f)onSelect(place,loc)}}}){
   drawRect(Color(0xffc3dbe6));val base=minOf(size.width/1000f,size.height/500f);val factor=base*zoom
   withTransform({translate(size.width/2,size.height/2);scale(factor,factor,pivot=Offset.Zero);translate(pan.x-500,pan.y-250)}){
    paths.forEach{drawPath(it,Color(0xffdde1c6));drawPath(it,Color(0xff6a806c),style=Stroke(.5f/zoom))}
    if(activeRoute!=null)route.zipWithNext().forEach{(a,b)->drawLine(Color(0xff9c3165),a,b,2f/factor)}
    pins.forEach{(place,_,p)->drawCircle(if(place.id==selected?.id)Color(0xffac233f)else Color(0xff18529d),if(place.id==selected?.id)6f/factor else 3f/factor,p)}
   }
  }
 }
 Column(modifier){Row{TextButton(onClick={zoom=(zoom*1.5f).coerceAtMost(64f)},modifier=Modifier.semantics{contentDescription=geoZoomDescription(language,true)}){Text("＋")};TextButton(onClick={zoom=(zoom/1.5f).coerceAtLeast(1f)},modifier=Modifier.semantics{contentDescription=geoZoomDescription(language,false)}){Text("−")};TextButton(onClick={zoom=1f;pan=Offset.Zero}){Text(text.reset)};TextButton(onClick={fullscreen=true}){Text(text.fullscreen)}};map(Modifier.fillMaxWidth().weight(1f),"geo-map")}
 if(fullscreen)Dialog(onDismissRequest={fullscreen=false},properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize().testTag("geo-atlas-fullscreen")){Column{Row{TextButton(onClick={fullscreen=false}){Text(text.back)};TextButton(onClick={zoom=(zoom*1.5f).coerceAtMost(64f)},modifier=Modifier.semantics{contentDescription=geoZoomDescription(language,true)}){Text("＋")};TextButton(onClick={zoom=(zoom/1.5f).coerceAtLeast(1f)},modifier=Modifier.semantics{contentDescription=geoZoomDescription(language,false)}){Text("−")};TextButton(onClick={zoom=1f;pan=Offset.Zero}){Text(text.reset)}};map(Modifier.fillMaxWidth().weight(1f),"geo-map-fullscreen")}}}
}
