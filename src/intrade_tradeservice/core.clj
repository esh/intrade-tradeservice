(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient))
	(:import (org.apache.commons.httpclient.methods PostMethod))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec))
	(:import (java.net URLEncoder)))

(defn urlencode [name-values]
	(apply str (interpose "&" (map #(apply str [
		(URLEncoder/encode (key %)) "=" (URLEncoder/encode (val %))])
		(seq name-values)))))

(defn post [site path]
	(let [client (new HttpClient)
	      method (new PostMethod path)]
		(try
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setHost (.getHostConfiguration client) site 80 "http")
			{:status (.executeMethod client method)
			 :body (new String (.getResponseBody method))}
			(finally (.releaseConnection method)))))

(defn login [url])

(defn logout)

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
