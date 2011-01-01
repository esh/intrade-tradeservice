(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient))
	(:import (org.apache.commons.httpclient.methods PostMethod))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec))
	(:import (java.net URLEncoder)))

(defn urlencode [name-values]
	(apply str (interpose "&" (map #(apply str [
		(URLEncoder/encode (key %)) "=" (URLEncoder/encode (val %))])
		(seq name-values)))))

(defn post [url]
	(let [client (new HttpClient)
	      method (new PostMethod url)]
		(try
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			{:status (.executeMethod client method)
			 :body (new String (.getResponseBody method))}
			(finally (.releaseConnection method)))))

(defn login [url])

(defn logout)

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
