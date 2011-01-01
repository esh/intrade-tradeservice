(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient HttpState NameValuePair))
	(:import (org.apache.commons.httpclient.methods GetMethod PostMethod))
	(:import (org.apache.commons.httpclient.params HttpMethodParams))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec)))

(def *user-agent* "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.1.7) Gecko/20100106 Ubuntu/9.10 (karmic) Firefox/3.5.7")

(def *cookies* (ref ()))

(defn http-req [method cookies params body]
	(let [client (new HttpClient)
	      init-state (new HttpState)]
		(try
			(dorun (map #(.addCookie init-state %) cookies))
			(dorun (map #(.setParameter (.getParams client) (key %) (val %)) (seq params)))
			(.setCookiePolicy (.getParams client) CookiePolicy/BROWSER_COMPATIBILITY)
			(.setState client init-state)
			(if (not (= nil body)) (.setRequestBody method (into-array NameValuePair (map #(new NameValuePair (key %) (val %)) (seq body)))))

			(let [status (.executeMethod client method)
			      cookies (seq (.getCookies (.getState client)))
			      location-header (.getResponseHeader method "location")
			      location (if (not (= nil location-header)) (.getValue location-header) nil)
			      body (new String (.getResponseBody method))]
				{:status status :cookies cookies :body body :location location})

			(finally (.releaseConnection method)))))

(defn http-post [url cookies params body] (http-req (new PostMethod url) cookies params body))


(defn http-get [url cookies params] (http-req (new GetMethod url) cookies params nil))

(defn login [url username password]
	(let [login1 (http-post
		"https://www.intrade.com"	
		()
		{HttpMethodParams/USER_AGENT *user-agent*
		"Referer" url}
		{"request_operation" "login"
		"request_type" "action"
		"contractBook" "none"
		"forwardpage" "intersiteLogin"
		"membershipNumber" username
		"password" password})
	      login2 (http-get
		(get login1 :location)
		(get login1 :cookies)
		{HttpMethodParams/USER_AGENT *user-agent*
		"Referer" url})]
		(dosync (ref-set *cookies* (get login2 :cookies)))
		(= 200 (get login2 :status))))

(defn logout)

(defn md []
	(http-get
		"http://play.intrade.com/jsp/intrade/trading/mdupdate.jsp?conID=318412&selConID=318412"
		(deref *cookies*)
		{HttpMethodParams/USER_AGENT *user-agent*}))

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
