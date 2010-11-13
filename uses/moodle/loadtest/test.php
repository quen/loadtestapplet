<?php
/**
 * Very basic load testing of Moodle system (CPU, database, file store).
 *
 * @copyright &copy; 2010 Samuel Marshall (done outside work time)
 * @author s.marshall@open.ac.uk
 * @license http://www.gnu.org/copyleft/gpl.html GNU Public License
 * @package local
 *//** */
require_once(dirname(__FILE__) . '/../../../config.php');
header("Content-Type: text/plain; encoding=UTF-8:");

// Security; this doesn't really do anything bad, but just in case it
// provides a better way for attackers to DoS the system than other pages
// in Moodle (doubt it), let's make it admin-only.
require_login();
require_capability('moodle/site:config',
	get_context_instance(CONTEXT_SYSTEM, SITEID));

// CPU test
///////////

// This test is about calling functions, because in Moodle, there's a lot
// of that about. 10,000 function calls sounds reasonable for a request.

$COUNT = 0;

function recurser($depth)
{
	global $COUNT;
	if($depth == 4)
	{
		$COUNT++;
		return;
	}
	for($i=0; $i<10; $i++)
	{
		recurser($depth + 1);
	}
}

$start = microtime(true);
recurser(0);
print "CPU: " . round(1000 * (microtime(true) - $start), 1) . "ms\n";

if($COUNT != 10000) {
	print "[Error] Wrong count: " . $COUNT;
	exit;
}

// RAM test
///////////

// This test is about copying memory around (results in a 16MB string)
raise_memory_limit('64M'); // Just in case
$string = 'This string contains 64 characters.                             ';
$start = microtime(true);
for($i=0; $i<18; $i++) { 
	$string = $string . $string;
}
print "RAM: " . round(1000 * (microtime(true) - $start), 1) . "ms\n";

// Database test
////////////////

// This test is about making a basic database read request. We'll do
// 50 which is probably similar to the number of database reads in a
// typical Moodle request. The test uses the config table because it is
// approximately the same size on all Moodle installations.

$start = microtime(true);
for($i=0; $i<50; $i++) {
	if (get_record('config', 'name', 'sillyname' . rand())) {
		print "[Error] Silly record should not exist";
	}
}
print "DB1: " . round(1000 * (microtime(true) - $start), 1) . "ms\n";

// Next test is about making the database do something more complicated.
// This is a bit of a rubbish test because as the config table fits in 
// memory it probably tests database CPU and not its ability to actually
// retrieve data - however in a fast Moodle installation, probably most
// requests should be serviced using in-memory data
$start = microtime(true);
$num = get_field_sql("
SELECT 
	COUNT(1)
FROM 
	{$CFG->prefix}config c
	LEFT JOIN {$CFG->prefix}config c1 on c1.id % 7 = c.id % 7
	LEFT JOIN {$CFG->prefix}config c2 on c2.id % 11 = c1.id % 11
WHERE
	c.id < 150 and c1.id < 150 and c2.id < 150
");
print "DB2: " . round(1000 * (microtime(true) - $start), 1) . "ms (result = $num)\n";

// Final test is about write data to log table. This could be an add_to_log
// call, but it isn't, because at the OU we have improved performance of
// add_to_log by a factor of ten or so, and I wanted our results to be
// comparable to others.
$logitem = (object)array('time'=>time(), 'userid'=>$USER->id,
	'ip'=>getremoteaddr(), 'course'=>$SITE->id, 'module'=>'report_loadtest',
	'cmid'=>0, 'action'=>'test', 'url'=>'', 'info'=>'');
$start = microtime(true);
insert_record('log', $logitem);
print "DB3: " . round(1000 * (microtime(true) - $start), 1) . "ms\n";


// Filesystem test
//////////////////

// This test checks dataroot performance by saving 1MB to it and
// then promptly deletes it again.

$start = microtime(true);
$filename = $CFG->dataroot . '/loadtest.temp.deleteme.' . rand();
file_put_contents($filename, substr($string, 0, 1*1024*1024));
unlink($filename);
print " FS: " . round(1000 * (microtime(true) - $start), 1) . "ms\n";

print "\nFinished OK\n";