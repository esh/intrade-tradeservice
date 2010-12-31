(ns tradeservice 
	(:require [clj-http.client :as client]))

(defn login [url]
	(client/get url))

(defn logout)

(defn send-order [order])

(defn cancel-order [order])

(defn add-listener [order])
