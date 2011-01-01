(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient NameValuePair))
	(:import (org.apache.commons.httpclient.methods GetMethod PostMethod))
	(:import (org.apache.commons.httpclient.params HttpMethodParams))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec)))

(def *user-agent* "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.1.7) Gecko/20100106 Ubuntu/9.10 (karmic) Firefox/3.5.7")

(defn http-post [protocol site path params body]
	(let [client (new HttpClient)
	      method (new PostMethod path)
	      cookie-spec (CookiePolicy/getDefaultSpec)]
		(try
			(dorun (map #(.setParameter (.getParams client) (key %) (val %)) (seq params)))
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setHost (.getHostConfiguration client) site (if (= protocol "https") 443 80) protocol)
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
			(.setParameter (.getParams client) HttpMethodParams/USER_AGENT *user-agent*)
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setHost (.getHostConfiguration client) site 80 "http")
			{:status (.executeMethod client method)
			 :cookies (seq (.match cookie-spec site 80 "/" false (.getCookies (.getState client))))
			 :body (new String (.getResponseBody method))}
			(finally (.releaseConnection method)))))

(defn login [site username password]
	(http-post "https" site "/"
		   {HttpMethodParams/USER_AGENT *user-agent*
		    "Referer" "http://play.intrade.com/jsp/intrade/contractSearch/"}
		   {"request_operation" "login"
		    "request_type" "action"
		    "contractBook" "none"
		    "forwardpage" "intersiteLogin"
		    "membershipNumber" username
		    "password" password}))

(defn logout)

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
