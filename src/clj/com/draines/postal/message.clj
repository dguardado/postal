(ns com.draines.postal.message
  (:use [clojure.contrib.test-is :only [run-tests deftest is]]
        [com.draines.postal.date :only [make-date]])
  (:import [java.util Properties]
           [javax.mail Session Message$RecipientType]
           [javax.mail.internet MimeMessage InternetAddress]))

(declare make-jmessage)

(defn recipients [msg]
  (let [jmsg (make-jmessage msg)]
    (map str (.getAllRecipients jmsg))))

(defn sender [msg]
  (or (:sender msg) (:from msg)))

(defn message->str [msg]
  (let [out (java.io.ByteArrayOutputStream.)
        jmsg (if (instance? MimeMessage msg) msg (make-jmessage msg))]
    (.writeTo jmsg out)
    (str out)))

(defn add-recipient! [jmsg rtype addr]
  (doto jmsg (.addRecipient rtype (InternetAddress. addr))))

(defn add-recipients! [jmsg rtype addrs]
  (if (string? addrs)
    (add-recipient! jmsg rtype addrs)
    (doseq [addr addrs]
      (add-recipient! jmsg rtype addr)))
  jmsg)

(defn add-multipart! [jmsg parts]
  (let [mp (javax.mail.internet.MimeMultipart.)]
    (doseq [part parts]
      (condp (fn [test type] (some #(= % type) test)) (:type part)
        [:inline :attachment] (.addBodyPart mp
                                            (doto (javax.mail.internet.MimeBodyPart.)
                                              (.attachFile (:content part))
                                              (.setDisposition (name (:type part)))))
        (.addBodyPart mp
                      (doto (javax.mail.internet.MimeBodyPart.)
                        (.setContent (:content part) (:type part))))))
    (.setContent jmsg mp)))

(defn make-jmessage
  ([msg]
     (let [{:keys [sender from host port]} msg
           props (doto (java.util.Properties.)
                   (.put "mail.smtp.host" (or host "not.provided"))
                   (.put "mail.smtp.port" (or port "25"))
                   (.put "mail.smtp.from" (or sender from)))
           session (Session/getInstance props)]
       (make-jmessage msg session)))
  ([msg session]
     (let [{:keys [from to cc bcc date subject body]} msg
           jmsg (MimeMessage. session)]
       (add-recipients! jmsg Message$RecipientType/TO to)
       (add-recipients! jmsg Message$RecipientType/CC cc)
       (add-recipients! jmsg Message$RecipientType/BCC bcc)
       (.setFrom jmsg (InternetAddress. from))
       (.setSubject jmsg subject)
       (if (string? body)
         (.setText jmsg body)
         (add-multipart! jmsg body))
       (.setSentDate jmsg (or date (make-date)))
       jmsg)))

(deftest test-simple
  (let [m {:from "fee@bar.dom"
           :to "Foo Bar <foo@bar.dom>"
           :cc ["baz@bar.dom" "quux@bar.dom"]
           :date (java.util.Date.)
           :subject "Test"
           :body "Test!"}]
    (is (= "Subject: Test" (re-find #"Subject: Test" (message->str m))))))

(deftest test-multipart
  (let [m {:from "foo@bar.dom"
           :to "baz@bar.dom"
           :subject "Test"
           :body [{:type "text/html"
                   :content "<b>some html</b>"}]}]
    (is (= "multipart/mixed" (re-find #"multipart/mixed" (message->str m))))
    (is (= "Content-Type: text/html" (re-find #"Content-Type: text/html" (message->str m))))
    (is (= "some html" (re-find #"some html" (message->str m))))))

(deftest test-inline
  (let [f (doto (java.io.File/createTempFile "_postal-" ".txt"))
        _ (doto (java.io.PrintWriter. f) (.println "tempfile contents") (.close))
        m {:from "foo@bar.dom"
           :to "baz@bar.dom"
           :subject "Test"
           :body [{:type :inline
                   :content f}]}]
    (is (= "tempfile" (re-find #"tempfile" (message->str m))))
    (.delete f)))

(deftest test-attachment
  (let [f (doto (java.io.File/createTempFile "_postal-" ".txt"))
        _ (doto (java.io.PrintWriter. f) (.println "tempfile contents") (.close))
        m {:from "foo@bar.dom"
           :to "baz@bar.dom"
           :subject "Test"
           :body [{:type :attachment
                   :content f}]}]
    (is (= "tempfile" (re-find #"tempfile" (message->str m))))
    (.delete f)))

(comment
  (run-tests))