package com.bibledesktop.myapp

import androidx.test.platform.app.InstrumentationRegistry
import com.bibledesktop.myapp.ui.study.*
import org.junit.Assert.*
import org.junit.Test

class GeoAtlasTest {
 @Test fun bundledGeographyHasPublishedCoordinatesVariantsAndExactVerseContext(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val data=context.assets.open("data/bible-geo.json").bufferedReader().use{parseGeoData(it.readText())}
  assertTrue(data.places.size>100);assertTrue(data.land.isNotEmpty());assertTrue(data.sources.any{it.license=="CC BY 4.0"})
  val place=data.places.first{it.locations.isNotEmpty()&&it.verses.isNotEmpty()}
  val reference=place.verses.first()
  assertTrue(geographicPlaces(data,"",listOf(reference)).any{it.id==place.id})
  assertTrue(geographicPlaces(data,"",listOf("Gen.99999.99999")).isEmpty())
  val location=place.locations.first();val point=projectGeo(location.lon,location.lat)
  assertTrue(point.x in 0f..1000f&&point.y in 0f..500f)
  assertEquals(listOf(place.id),geographicPlaces(data,place.name,listOf(reference)).filter{it.id==place.id}.map{it.id})
  data.routes.forEach{route->route.stops.forEach{stop->assertTrue(data.places.any{it.id==stop.placeId&&stop.osis in it.verses&&it.locations.isNotEmpty()})}}
  assertEquals(listOf("paul-first","abraham","paul-macedonia","jesus-galilee"),data.routes.map{it.id})
  val philippi=data.places.first{it.id=="a49e1d0"}
  assertEquals(listOf("paul-macedonia"),geographicRoutes(data,philippi.id).map{it.id})
  val abraham=data.routes.first{it.id=="abraham"}
  assertEquals(8,geoRoutePoints(data,abraham,emptyMap()).size)
  assertEquals(7,geoRoutePlaces(data,abraham).size)
  val bethel=data.places.first{it.id=="a64f355"}
  val alternative=bethel.locations.last()
  val points=geoRoutePoints(data,abraham,mapOf(bethel.id to alternative.id))
  assertEquals(projectGeo(alternative.lon,alternative.lat),points[3]);assertEquals(points[3],points[6])
 }
 @Test fun projectionRejectsInvalidCoordinates(){
  assertEquals(androidx.compose.ui.geometry.Offset(500f,250f),projectGeo(0.0,0.0))
  assertTrue(runCatching{projectGeo(0.0,91.0)}.isFailure)
 }
}
