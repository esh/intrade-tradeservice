(ns tradeservice 
	(:require [clj-http.client :as client])
	(:import [java.net URLEncoder]))

(defn urlencode [name-values]
	(apply str (interpose "&" (map #(apply str [
		(URLEncoder/encode (key %)) "=" (URLEncoder/encode (val %))])
		(seq name-values)))))


(defn login [url]
	(client/get url))

(defn logout)

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
