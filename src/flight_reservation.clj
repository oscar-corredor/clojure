(ns flight-reservation
  (:require [clojure.string]
            [clojure.pprint]
            #_[input-simple :as input]
            [input-random :as input]))

(def logger (agent nil))
(defn log [& msgs] (send logger (fn [_] (apply println msgs))))
;(defn log [& msgs] nil)

(def flights
  "All flights are encapsulated in a single atom in this implementation.
  You are free to change this to a more appropriate mechanism."
  (atom []))

(defn initialize-flights [initial-flights]
  "Set `flights` atom to the `initial-flights`."
  (reset! flights initial-flights))

(defn print-flights [flights]
  "Print `flights`."
  (letfn [(pricing->str [pricing]
            (->> pricing
              (map (fn [[p a t]] (clojure.pprint/cl-format nil "$~3d: ~3d ~3d" p a t)))
              (clojure.string/join ", ")))]
    (doseq [{:keys [id from to pricing]} flights]
      (println (clojure.pprint/cl-format nil "Flight ~3d from ~a to ~a: ~a"
        id from to (pricing->str pricing))))))

(defn- update-pricing [flight factor]
  "Updated pricing of `flight` with `factor`."
  (update flight :pricing
    #(map (fn [[p a t]] [(* p factor) a t]) %)))

(defn start-sale [carrier]
  "Sale: all flights of `carrier` -20%."
  (log "Start sale for" carrier "!")
  (swap! flights
    (fn [old-flights]
      (vec (map
             (fn [flight]
               (if (= (:carrier flight) carrier)
                 (update-pricing flight 0.80)
                 flight))
             old-flights)))))

(defn end-sale [carrier]
  "End sale: all flights of `carrier` +25% (inverse of -20%)."
  (log "End sale for" carrier "!")
  (swap! flights
    (fn [old-flights]
      (vec (map
             (fn [flight]
               (if (= (:carrier flight) carrier)
                 (update-pricing flight 1.25)
                 flight))
             old-flights)))))

(defn sort-pricing [pricing]
  "Sort `pricing` from lowest to highest price."
  (sort-by first pricing))

(defn filter-pricing-with-n-seats [pricing seats]
  "Get `pricing` for which there are at least `seats` empty seats available."
  (filter #(>= (second %) seats) pricing))

(defn lowest-available-price [flight seats]
  "Returns the lowest price in `flight` for which at least `seats` empty seats
  are available, or nil if none found."
  (-> (:pricing flight)                 ; [[price available taken]]
    (filter-pricing-with-n-seats seats)
    (sort-pricing)
    (first)                             ; [price available taken]
    (first)))                           ; price

(defn- find-flight [flights customer]
  "Find a flight in `flights` that is on the route and within the budget of
  `customer`. If a flight was found, returns {:flight flight :price price},
  else returns nil."
  (let [{:keys [_id from to seats budget]}
          customer
        flights-and-prices
          ; flights that are on the route and within budget, and their price
          (for [f flights
                :when (and (= (:from f) from) (= (:to f) to))
                :let [lowest-price (lowest-available-price f seats)]
                :when (and (some? lowest-price) (<= lowest-price budget))]
            {:flight f :price lowest-price})
        cheapest-flight-and-price
          (first (sort-by :price flights-and-prices))]
    cheapest-flight-and-price))

(defn- book [flight price seats]
  "Updates `flight` to book `seats` at `price`."
  (update flight :pricing
    (fn [pricing]
      (for [[p a t] pricing]
        (if (= p price)
          [p (- a seats) (+ t seats)]
          [p a t])))))

(defn- process-customer [flights customer]
  "Try to book a flight from `flights` for `customer`, returning the updated
  flight if found, or nil if no suitable flight was found."
  (if-let [{:keys [flight price]} (find-flight flights customer)]
    (let [updated-flight (book flight price (:seats customer))]
      (log "Customer" (:id customer) "booked" (:seats customer)
        "seats on flight" (:id updated-flight) "at $" price " (< budget of $"
        (:budget customer) ").")
      updated-flight)
    (do
      (log "Customer" (:id customer) "did not find a flight.")
      nil)))

(def finished-processing?
  "Set to true once all customers have been processed, so that sales process
  can end."
  (atom false))

(defn process-customers [customers]
  "Process `customers` one by one."
  (doseq [customer customers]
    (swap! flights
      (fn [flights]
        (if-let [updated-flight (process-customer flights customer)]
          (assoc flights (:id updated-flight) updated-flight)
          flights))))
  (reset! finished-processing? true))

(defn sales-process []
  "The sales process starts and ends sales periods, until `finished-processing?`
  is true."
  (loop []
    (let [discounted-carrier (rand-nth input/carriers)]
      (Thread/sleep input/TIME_BETWEEN_SALES)
      (start-sale discounted-carrier)
      (Thread/sleep input/TIME_OF_SALES)
      (end-sale discounted-carrier))
    (if (not @finished-processing?)
      (recur))))

(defn main []
  (initialize-flights input/flights)
  (let [f1 (future (time (process-customers input/customers)))
        f2 (future (sales-process))]
    @f1
    @f2)
  (println "Flights:")
  (print-flights @flights))

(main)
(shutdown-agents)
