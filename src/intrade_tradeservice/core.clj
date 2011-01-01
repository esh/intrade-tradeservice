(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient NameValuePair))
	(:import (org.apache.commons.httpclient.methods GetMethod PostMethod))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec)))

(defn pairs [body] 
	(map #(new NameValuePair (key %) (val %)) (seq body))) 

(defn http-post [site path body]
	(let [client (new HttpClient)
	      method (new PostMethod path)
	      cookie-spec (CookiePolicy/getDefaultSpec)]
		(try
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setHost (.getHostConfiguration client) site 80 "http")
			(.setRequestBody method (into-array NameValuePair (map #(new NameValuePair (key %) (val %)) (seq body))))
			{:status (.executeMethod client method)
			 :cookies (seq (.match cookie-spec site 80 "/" false (.getCookies (.getState client))))
			 :body (new String (.getResponseBody method))}
			(finally (.releaseConnection method)))))

(defn http-get [site path]
	(let [client (new HttpClient)
	      method (new GetMethod path)
	      cookie-spec (CookiePolicy/getDefaultSpec)]
		(try
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setHost (.getHostConfiguration client) site 80 "http")
			{:status (.executeMethod client method)
			 :cookies (seq (.match cookie-spec site 80 "/" false (.getCookies (.getState client))))
			 :body (new String (.getResponseBody method))}
			(finally (.releaseConnection method)))))

(defn login [url])

(defn logout)

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
