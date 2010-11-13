<?php
$string['loadtest']='Load test';
$string['intro']='This report includes a simple load test to evaluate the performance of your system. It includes the performance of the database, filesystem, memory, and CPU. All tests are likely to be inaccurate due to caching and other constraints.';
$string['warning']='Warning: If this is a live system, performance for other users may be significantly affected during the test run.';
$string['requestspersecondattempt'] = 'Requests/s (attempted)';
$string['requestspersecondactual'] = 'Requests/s (actual)';
$string['mediantime'] = 'Median time';
$string['successful'] = 'Successful';
$string['results'] = 'Results';
$string['fail_retry'] = 'Fail, waiting for retry (10 second delay)';
$string['fail_stop'] = 'Test completed (server cannot cope with this rate)';
$string['waiting'] = 'Waiting (10 second delay before next test part)...';
$string['starttest'] = 'Start test';
$string['stoptest'] = 'Stop test';
$string['testinfo'] = '
<p>Once you start the test, it runs for 20 seconds, pauses for 10 seconds, then increases the number of requests per second and repeats. This continues until the system cannot deliver the desired number of requests per second.</p>
<p>Should you wish to stop the test before that, click the stop button; the test will stop after the current part completes.</p>
<p><i>Note: If you see values for actual requests/s which are notably lower than attempted requests/s, even when the server is not stressed and is completing all requests quickly, this may be because your browser is coping poorly. The same applies if results do not update in the results table immediately after a test part finishes. Switch to a different browser and try again.</i></p>
<p>The result number (will be highlighted in table) is the number of requests per second your server can handle, using the test load. You should probably run the test multiple times to check that the result is consistent.</p>
<p>This number of requests per second is not necessarily exactly the number of genuine Moodle requests per second that you can handle, but it might be somewhat related to that number.</p>
<p>The load test applet makes a maximum of 20 simultaneous requests. If your server(s) benefit from very high parallelism, this might be a limiting factor. This is indicated when the median time remains similar to the 1-request time, but the load test window still fills up and the system fails due to actual requests being less than attempted (I would expect this to happen only if your server can handle about 80 requests/second).</p> 
<p>By the way, should you wish to see the individual test request page running, <a href=\'test.php\'>here\'s a link to it</a>.</p>
<p><small>PHP, JavaScript and Java applet by sam marshall / <a href=\'http://www.leafdigital.com/\'>leafdigital</a></small></p>
';

