(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient HttpState NameValuePair))
	(:import (org.apache.commons.httpclient.methods GetMethod PostMethod))
	(:import (org.apache.commons.httpclient.params HttpMethodParams))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec))
	(:import (java.text SimpleDateFormat)))

(def *user-agent* "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.1.7) Gecko/20100106 Ubuntu/9.10 (karmic) Firefox/3.5.7")

(def *state* (ref 'logged-out))
(def *quotes* (agent {}))
(def *cookies* (ref ()))
(def *url* (ref ""))

(defn http-req [method cookies params body]
	(let [client (new HttpClient)
	      init-state (new HttpState)]
		(try
			(dorun (map #(.addCookie init-state %) cookies))
			(dorun (map
				#(.setParameter 
					(.getParams client)
					(key %)
					(val %))
				(seq params)))
			(.setCookiePolicy
				(.getParams client)
				CookiePolicy/BROWSER_COMPATIBILITY)
			(.setState client init-state)

			(if (not (= nil body))
				(.setRequestBody
					method
					(into-array
						NameValuePair
						(map #(new NameValuePair 
							(key %)
							(val %))
						     (seq body)))))

			(let [status (.executeMethod client method)
			      cookies (seq (.getCookies (.getState client)))
			      location-header (.getResponseHeader method "location")
			      location (if (not (= nil location-header)) (.getValue location-header) nil)
			      body (new String (.getResponseBody method))]
				{:status status :cookies cookies :body body :location location})

			(finally (.releaseConnection method)))))

(defn http-post [url cookies params body] (http-req (new PostMethod url) cookies params body))

(defn http-get [url cookies params](http-req (new GetMethod url) cookies params nil))

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
		(dosync
			(ref-set *cookies* (get login2 :cookies))
			(ref-set *url* url)
			(if (= 200 (get login2 :status))
				(= 'logged-in (ref-set *state* 'logged-in))
				(throw (new Exception "could not login"))))))

(defn logout [] (dosync (do (ref-set *cookies* ()) (ref-set *state* 'logged-out))))

(defn bind-quote [contract-id] (send *quotes* #(assoc % contract-id {})))

(def get-quote (memoize (fn [contract-id]
	(let [parser
		#(let [s (.split (.substring % 7 (- (.length %) 1)) ",")
		       qty (Integer/parseInt (nth s 1))
		       price (Float/parseFloat (.substring
				(nth s 2)
				1
				(- (.length (nth s 2)) 1)))]
			{:qty qty :price price})
	      getter 
		#(http-get
			(apply str [(deref *url*)
				    "jsp/intrade/trading/mdupdate.jsp?conID="
				    % 
				    "&selConID="
				    %])
			(deref *cookies*)
			{HttpMethodParams/USER_AGENT *user-agent*})
	      extractor
		#(let[contract-id (get % :contract-id)
		      res (getter contract-id)
		      status (get res :status)
		      body (get res :body)]
			(if (= status 200) 
				{:contract-id contract-id 
				 :timestamp (.parse 
					(new SimpleDateFormat "h:mm:ssa z")
					(first (re-seq
						#"\d{1,2}:\d{2}:\d{2}\w{2} GMT"
						body))) 
				 :bids (map parser (re-seq #"setBid\(.*\)" body))
				 :offers (map parser (re-seq #"setOffer\(.*\)" body))}
			(throw (new Exception
				(apply str ["get-md got "
					    status
					    " from server"])))))
	      quote (agent {:contract-id contract-id})]
		(.start (new Thread (fn [] (loop []
			(if (= 'logged-in @*state*)
				(send quote #(extractor %)))
			(Thread/sleep 1000)	
			(recur)))))
		quote))))
	      						
(defn send-order [order])

(defn cancel-order [])


