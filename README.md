Light-WS
========

Sometimes a full blown spring framework is not an option, and neither is
an application server (e.g. when resources are limited). This library is a
lightweight REST webservice implementation. It is based on jetty and is capable
of starting a webservice on a low-end device like the Raspberry Pi in a few seconds
(compared to minutes for spring-boot).

license
=======
Light-WS is licensed under MIT license and can therefore be used in any project, even
for commercial ones.

build
=====

    mvn clean package

example
=======
```java
@WebService("/ws")
public class MyWebService {

    @GetMapping("/get/some/info/{id}")
    public String getSomeInfo(@GetParameter("id") String id) {
        return "Hello " + id;
    }

    @PostMapping("/post/sth")
    public void getSomePost(@PostParameter MyObject obj) {
        // (expects the post parameter to be type application/json)
        // do sth. with obj ...
    }

    @PostMapping("/upload")
    @ResultMimeType("text/html; charset=utf-8") //without this the default mime type is text/plain
    public String uploadFile(@PostParameter UploadedFile uploadedFile) {
        // use uploadedFile.openInputStream() to open the uploaded file for reading
        return "<html><h1>Uploaded successfully</h1></html>";
    }

}
```

Then you can start the webserver like this:
```java
Server server = new Server(8080);
server.setHandler(new HandlerList(
        new WebServiceHandler(new MyWebService())
        //...
));
server.start();
```

Now you should be able to open http://localhost:8080/ws/get/some/info/123 and
get a nice greeting. And you should also be able to post a JSON to /ws/post/sth.

Note that you can also mix get(path) parameters and post like this:
```java
@PostMapping("/post/sth/{name}")
public void getSomePost(@PostParameter MyObject obj,
                @GetParameter("name") String name) {
    // (expects the post parameter to be type application/json)
    // do sth. with obj ...
}
```
