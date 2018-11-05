Light-WS
========

Sometimes a full the blown spring framework is not an option, and neither is
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
    @WebService("/ws")
    public class MyWebService {

        @GetMapping("get/some/info/{id}")
        public String getSomeInfo(@GetParameter("id") String id) {
            return "Hello " + id;
        }

    }

Then you can start the webserver like this:

    Server server = new Server(8080);
    server.setHandler(new HandlerList(
            new WebServiceHandler(new MyWebService())
            //...
    ));
    server.start();

Now you should be able to open ´http://localhost:8080/ws/get/some/info/123` and
get a nice greeting.