package com.bibledesktop.myapp.ui.study

import androidx.compose.ui.geometry.Offset
import org.json.JSONObject

internal data class GeoLocation(val id:String,val name:String,val lon:Double,val lat:Double,val score:Double?,val type:String)
internal data class GeoPlace(val id:String,val name:String,val aliases:List<String>,val verses:List<String>,val locations:List<GeoLocation>)
internal data class GeoSource(val name:String,val url:String,val license:String,val licenseUrl:String?)
internal data class GeoStop(val placeId:String,val osis:String)
internal data class GeoRoute(val id:String,val source:String,val stops:List<GeoStop>)
internal data class GeoData(val referenceSystem:String,val places:List<GeoPlace>,val land:List<List<Offset>>,val sources:List<GeoSource>,val routes:List<GeoRoute>)
internal fun projectGeo(lon:Double,lat:Double):Offset {require(lon.isFinite()&&lat.isFinite()&&kotlin.math.abs(lon)<=180.001&&kotlin.math.abs(lat)<=90.001);return Offset(((lon+180)/360*1000).toFloat(),((90-lat)/180*500).toFloat())}
internal fun geographicPlaces(data:GeoData,query:String,references:List<String>):List<GeoPlace>{val wanted=references.toSet();return data.places.filter{place->(wanted.isEmpty()||place.verses.any{it in wanted})&&(query.isBlank()||(listOf(place.name)+place.aliases+place.locations.map{it.name}).any{it.contains(query.trim(),ignoreCase=true)})}}
internal fun parseGeoData(raw:String):GeoData {
 val root=JSONObject(raw);require(root.getInt("schema")==1)
 val places=root.getJSONArray("places");val land=root.getJSONArray("land");val sources=root.getJSONArray("sources");val routes=root.optJSONArray("routes")
 fun strings(row:JSONObject,key:String)=row.optJSONArray(key)?.let{array->(0 until array.length()).map{array.getString(it)}}?:emptyList()
 return GeoData(root.getString("referenceSystem"),(0 until places.length()).map{index->val row=places.getJSONObject(index);val locations=row.getJSONArray("locations");GeoPlace(row.getString("id"),row.getString("name"),strings(row,"aliases"),strings(row,"verses"),(0 until locations.length()).map{n->val loc=locations.getJSONObject(n);val lon=loc.getDouble("lon");val lat=loc.getDouble("lat");projectGeo(lon,lat);GeoLocation(loc.getString("id"),loc.getString("name"),lon,lat,if(loc.isNull("score"))null else loc.getDouble("score"),loc.getString("type"))})},(0 until land.length()).map{n->val ring=land.getJSONArray(n);(0 until ring.length()).map{m->val point=ring.getJSONArray(m);projectGeo(point.getDouble(0),point.getDouble(1))}},(0 until sources.length()).map{n->val source=sources.getJSONObject(n);GeoSource(source.getString("name"),source.getString("url"),source.getString("license"),source.optString("license_url").takeIf{it.isNotBlank()})},routes?.let{(0 until it.length()).map{n->val row=it.getJSONObject(n);val stops=row.getJSONArray("stops");GeoRoute(row.getString("id"),row.getString("source"),(0 until stops.length()).map{m->val stop=stops.getJSONObject(m);GeoStop(stop.getString("place_id"),stop.getString("osis"))})}}?:emptyList())
}
