package edu.famaf.paradigmas.parser

import scalaj.http.{Http, HttpResponse}
import scala.xml.XML
import org.json4s._

import org.json4s.jackson.JsonMethods._


abstract class Parser {
    val emptyText: String
    
    // Get the content
    def openURL(url: String): String = {
        try {
            var response = Http(url)
            .timeout(connTimeoutMs = 2000, readTimeoutMs = 5000)
            .asString
            response.body
        } catch {
            case e : Throwable  => println("Error in HTTP response")
            emptyText 
        }
    }
    
    // Extract text 
    def processText(rawText: String): Seq[String] 
    
    // Read the text
    def readText(url: String): Seq[String] = {
        processText(openURL(url))
    }
}

class RSSParser() extends Parser {
    val emptyText = "<rss></rss>"

    def processText(rawText: String): Seq[String] = {
        // convert the `String` to a `scala.xml.Elem`
        val xml = XML.loadString(rawText)
        // Extract text from title and description
        (xml \\ "item").map { item =>
            ((item \ "title").text ++ " " ++ (item \ "description").text)
        }
    }
}

class RedditParser() extends Parser {
    val emptyText = "{}"
    implicit val formats = DefaultFormats
    
    def processText(rawText: String): Seq[String] = {
        val urlPattern = "(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
        val titles = (parse(rawText) \ "data" \ "children" \ "data" \ "title" )
         .extract[List[String]]
        val selftexts = (parse(rawText) \ "data" \ "children" \ "data" \ "selftext" )
         .extract[List[String]]
        val result = titles.zip(selftexts).map{case (a,b) => a ++ " " ++b}
        result.map(text => text.replaceAll(urlPattern, " "))
    }
}