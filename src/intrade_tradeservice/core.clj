(ns tradeservice 
	(:import (org.apache.commons.httpclient HttpClient HttpState NameValuePair))
	(:import (org.apache.commons.httpclient.methods GetMethod PostMethod))
	(:import (org.apache.commons.httpclient.params HttpMethodParams))
	(:import (org.apache.commons.httpclient.cookie CookiePolicy CookieSpec))
	(:import (java.text SimpleDateFormat))
	(:import (java.lang StringBuilder))
	(:import (java.io BufferedReader InputStreamReader)))

(def *user-agent* "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.1.7) Gecko/20100106 Ubuntu/9.10 (karmic) Firefox/3.5.7")

(def *state* (atom 'logged-out))
(def *url* (atom ""))
(def *cookies* (atom ()))
(def *active-orders* (atom {}))

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
			      body (with-open [stream (.getResponseBodyAsStream method)]
					(apply str (line-seq (new BufferedReader (new InputStreamReader stream)))))]
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
		(compare-and-set! *cookies* @*cookies* (get login2 :cookies))
		(compare-and-set! *url* @*url* url)
		(if (= 200 (get login2 :status))
			(compare-and-set! *state* @*state* 'logged-in)
			(throw (new Exception "could not login")))))

(defn logout [] (do
	(compare-and-set! *cookies* @*cookies* ())
	(compare-and-set! *state* @*state* 'logged-out)))

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
			(str (deref *url*)
			     "jsp/intrade/trading/mdupdate.jsp?conID="
			     % 
			     "&selConID="
			     %)
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
				(str "get-md got " status " from server")))))
	      quote (agent {:contract-id contract-id})]
		(.start (new Thread (fn [] (loop []
			(if (= 'logged-in @*state*)
				(send quote #(extractor %)))
			(Thread/sleep 1000)	
			(recur)))))
		quote))))

(defn send-order [& {:keys [contract-id side price qty tif]}]
	(let [side (when side (case 'Buy "B") (case 'Sell "S"))
	      tif (when tif (case 'GFS' "gfs") (case 'FOK "fok" ))
	      res (http-post
		@*url*
		@*cookies* 
		{HttpMethodParams/USER_AGENT *user-agent*}
		{"contractID" (str contract-id)
		 "killtime" nil	
		 "limitPrice" (str price)
		 "minutesTillExpiry" nil	
		 "orderType" "L"
  		 "originalQuantity" (str qty)
		 "quantity" (str qty) "request_operation" "enterOrder" "request_type" "request"
		 "resetLifetime" tif
		 "side" side 
		 "timeInForce" "2"
		 "touchPrice" nil	
		 "type" "L"})
		body (get res :body)]
		(if (re-seq #"order has been accepted" body)
			(let [order-id (Integer/parseInt 
				(nth (first (re-seq #"Order ID\D+(\d+)" body)) 1))
			      order (agent {:order-id order-id
					    :contract-id contract-id
					    :side side
					    :tif tif 
					    :qty qty
				 	    :state 'New})]
				(swap!
					*active-orders*
					merge
					@*active-orders*
					{order-id order}))
			(agent {:contract-id contract-id
			        :side side
			        :tif tif 
				:qty qty
			 	:state 'Rejected}))))
		
(defn cancel-order [])

(defn check-order []
	(let [res (http-get
		(str @*url*
		     "jsp/intrade/trading/t_p.jsp?reportType=1&fType=0&statusFilter=5&dateFilter=0&filter=All")
		@*cookies*
		{HttpMethodParams/USER_AGENT *user-agent*})]
		(doall (map
			#(let [s (.split
					(.replaceAll (.replaceAll % "<.+?>" " ") " +" " ")
					" ")
			       order-id (Integer/parseInt (nth s 1))
			       qty (Integer/parseInt (nth s 4))
			       cum-qty (- qty (Integer/parseInt (nth s 5)))
			       avg-price (Float/parseFloat (nth s 6))
			       state (symbol (nth s 12))
			       old-order (get @*active-orders* order-id)
			       new-order {:order-id order-id 
					  :qty qty 
					  :cum-qty cum-qty
					  :avg-price avg-price
					  :state state}]
				(if (nil? old-order)
					(swap!
						*active-orders*
						merge
						@*active-orders*
						{order-id (agent new-order)})
					(if (not (= (get @old-order :state) (get new-order :state)))
						(send
							old-order	
							merge
							@old-order
							new-order))))
			(re-seq
				#"<tr class=reportRow.+?/tr>"
				(.replaceAll (get res :body) "(\r\n)|\t" ""))))))

(.start (new Thread (fn []
	(loop []
		(if (= 'logged-in @*state*)
			(check-order))
		(Thread/sleep 1000)	
		(recur)))))
