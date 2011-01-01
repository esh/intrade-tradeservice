(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient HttpState NameValuePair))
	(:import (org.apache.commons.httpclient.methods GetMethod PostMethod))
	(:import (org.apache.commons.httpclient.params HttpMethodParams))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec)))

(def *user-agent* "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.1.7) Gecko/20100106 Ubuntu/9.10 (karmic) Firefox/3.5.7")

(defn http-post [protocol site path cookies params body]
	(let [client (new HttpClient)
	      method (new PostMethod path)
	      init-state (new HttpState)
	      port (if (= protocol "https") 443 80)]
		(try
			(dorun (map #(.addCookie init-state %) cookies))
			(dorun (map #(.setParameter (.getParams client) (key %) (val %)) (seq params)))
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setHost (.getHostConfiguration client) site port protocol)
			(.setState client init-state)
			(.setRequestBody method (into-array NameValuePair (map #(new NameValuePair (key %) (val %)) (seq body))))

			(let [status (.executeMethod client method)
			      cookies (seq (.getCookies (.getState client)))
			      location-header (.getResponseHeader method "location")
			      location (if (not (= nil location-header)) (.getValue location-header) nil)
			      body (new String (.getResponseBody method))]
				{:status status :cookies cookies :body body :location location})

			(finally (.releaseConnection method)))))

(defn http-get [protocol site path cookies]
	(let [client (new HttpClient)
	      method (new GetMethod path)
	      init-state (new HttpState)
	      port (if (= protocol "https") 443 80)]
		(try
			(dorun (map #(.addCookie init-state %) cookies))
			(.setParameter (.getParams client) HttpMethodParams/USER_AGENT *user-agent*)
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setHost (.getHostConfiguration client) site 80 "http")
			(.setState client init-state)

			{:status (.executeMethod client method)
			 :cookies (seq (.getCookies (.getState client)))
			 :body (new String (.getResponseBody method))}
			(finally (.releaseConnection method)))))

(defn login [site username password]
	(let [login1 (http-post "https" site "/" ()
		   {HttpMethodParams/USER_AGENT *user-agent*
		    "Referer" "http://play.intrade.com/jsp/intrade/contractSearch/"}
		   {"request_operation" "login"
		    "request_type" "action"
		    "contractBook" "none"
		    "forwardpage" "intersiteLogin"
		    "membershipNumber" username
		    "password" password})
	      ]
		login1))

(defn logout)

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
