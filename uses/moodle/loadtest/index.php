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
require_once($CFG->libdir . '/adminlib.php');

// Test permissions.
require_login();
require_capability('moodle/site:config',
	get_context_instance(CONTEXT_SYSTEM, SITEID));

admin_externalpage_setup('reportloadtest');
admin_externalpage_print_header();

// Find applet date
$handle = opendir(dirname(__FILE__));
$appletdate = false;
while(($file = readdir($handle)) !== false) {
	$matches = array();
	if(preg_match('~^loadtestapplet\.([0-9]+)\.jar$~', $file, $matches)) {
		$appletdate = $matches[1];
		break;
	}
}
closedir($handle);
if(!$appletdate) {
	error('Missing applet file loadtestapplet.(date).jar');
}

print '<p>' . get_string('intro', 'report_loadtest') . '</p>';
print '<p><strong>' . get_string('warning', 'report_loadtest') . '</strong></p>';

?>
<script type="text/javascript">
var testTime = 20000;
var delayTime = 10000;

var requestsPerSecond;
var increaseRate;

var passFailed = 0;
var numRequests;
var numSuccesses;
var allTimes;
var startTime;

var lastOkResult;

var stopRequest;

function go()
{
	// Disable button
	document.getElementById('go').disabled = true;
	document.getElementById('stop').disabled = false;
	stopRequest = false;

	// Clear results	
	var trs = document.getElementById('results').getElementsByTagName('tr');
	for(var i=trs.length-1; i>=1; i--)
	{
		trs.item(i).parentNode.removeChild(trs.item(i));
	}
	
	// Set initial requests per second
	requestsPerSecond = 1;
	increaseRate = 2;
	lastOkResult = null;
	startTest();
}

function stop()
{
	document.getElementById('stop').disabled = true;
	stopRequest = true;
}
	
function startTest()
{
	var applet = document.getElementById('applet');
	var url = '<?php print $CFG->wwwroot ?>/admin/report/loadtest/test.php';
	var pattern = 'Finished OK';
	
	numSuccesses = 0;
	allTimes = [];
	
	var delay = 1000 / requestsPerSecond;
	numRequests = 0;
	for(var time=0; time<testTime; time+=delay)
	{
		applet.loadTestEvent(Math.floor(time), url, pattern); 
		numRequests++;
	}
	applet.loadTestStart();
	startTime = new Date().getTime();
}

function addCell(tr, text)
{
	var index;
	if(tr.lastIndex)
	{
		index = tr.lastIndex + 1;
	}
	else
	{
		index = 0;
	}
	tr.lastIndex = index;
	
	var td = document.createElement('td');
	if(index == 3)
	{
		td.className = 'cell lastcol';
	}
	else
	{
		td.className = 'cell c' + index;
	}
	td.appendChild(document.createTextNode(text));
	tr.appendChild(td);
	return td;
}

function checkResults(currentResult)
{
	// 1. Fail if success < 100
	if(currentResult.success < 100.0)
	{
		return false;
	}
	
	// 2. Fail if median time increased more than 50%
	if(lastOkResult && currentResult.median > lastOkResult.median * 1.5)
	{
		return false;
	}
	
	// 3. Fail if attempted/actual not close
	if(currentResult.actual < currentResult.attempted * 0.9)
	{
		return false;
	}
	
	return true;
}

function loadTestFinished()
{
	var applet = document.getElementById('applet');
	applet.loadTestReset();

	var endTime = new Date().getTime();
	var actualRPS = Math.round(100 * (numRequests * 1000) / (endTime - startTime)) / 100;
	
	allTimes.sort(function(a,b){return a - b});
	var medianTime = allTimes[Math.floor(allTimes.length / 2)];
	var successPercentage = Math.round(10 * (numSuccesses * 100) / numRequests) / 10;
	
	var results = document.getElementById('results');
	var tr = document.createElement('tr');
	results.appendChild(tr);
	
	addCell(tr, requestsPerSecond);
	var actualCell = addCell(tr, actualRPS);
	addCell(tr, medianTime + ' ms');
	addCell(tr, successPercentage + '%');
	
	// Add information line
	tr = document.createElement('tr');
	results.appendChild(tr);
	var td = document.createElement('td');
	tr.appendChild(td);
	td.colSpan = 4;
	td.style.padding = '4px';
	td.style.border = 'none';
	td.style.background = 'white';
	var span = document.createElement('span');
	td.appendChild(span);
	span.style.padding = '4px';
	span.style.background = '#eee';
	
	// Get current result
	var currentResult = { attempted: requestsPerSecond, actual: actualRPS,
		success: successPercentage, median: medianTime, cell:actualCell };
	
	// Is it ok?
	var ok = checkResults(currentResult);
	
	// Decide whether to increase
	if(!ok)
	{
		passFailed++;
		if(passFailed >= 3)
		{
			// Didn't manage to do that many requests per second, so stop
			span.appendChild(document.createTextNode('<?php print_string("fail_stop", 	"report_loadtest"); ?>')); 
	
			// Enable button again, and stop
			document.getElementById('go').disabled = false;
			
			// Highlight the last OK score
			if(lastOkResult)
			{
				var cells = results.getElementsByTagName('td');
				for(var i=0; i<cells.length - 1; i++)
				{
					cells.item(i).style.background = '#eee';
				}
				lastOkResult.cell.style.border = '2px solid green';
				lastOkResult.cell.style.background = 'white';
			}
			return;
		}
		else
		{
			// Retry, maybe there was congestion at that point
			requestsPerSecond -= increaseRate;
			if(increaseRate > 0.25)
			{
				increaseRate /= 2;
			}
			span.appendChild(document.createTextNode('<?php print_string("fail_retry", 	"report_loadtest"); ?>')); 
		}
	}
	else
	{
		// Reset fail count
		passFailed = 0;
		
		// Store these as successful results
		lastOkResult = currentResult;
		
		// Add waiting message
		span.appendChild(document.createTextNode('<?php print_string("waiting", "report_loadtest"); ?>')); 
	}
	
	setTimeout(function()
	{
		results.removeChild(tr);
		
		if(stopRequest)
		{
			document.getElementById('go').disabled = false;
			return;
		}
		requestsPerSecond += increaseRate;
		startTest();
	}, stopRequest ? 0 : delayTime);
}

function loadTestResult(index, ms, result)
{
	if(result)
	{
		numSuccesses++;
	}
	allTimes.push(ms);
}
</script>

<div style="margin: 2em 0">
<div>
<applet code="com.leafdigital.loadtestapplet.LoadTestApplet" width="502" height="282" archive="loadtestapplet.<?php print $appletdate; ?>.jar" id="applet">
</applet>
</div>

<div>
<button id='go' onclick='go()'><?php print_string('starttest', 'report_loadtest'); ?></button>
<button id='stop' onclick='stop()' disabled='disabled'><?php print_string('stoptest', 'report_loadtest'); ?></button>

<script type="text/javascript">
// This is annoying
document.getElementById('go').disabled = false;
</script>

</div>
</div>
<?php print_string('testinfo', 'report_loadtest'); ?>

<h3><?php print_string('results', 'report_loadtest'); ?></h3>

<table id="results" class="flexible generaltable generalbox boxalignleft">
<tr>
<th class="header c0" scope="col"><?php print_string('requestspersecondattempt', 'report_loadtest'); ?></th>
<th class="header c1" scope="col"><?php print_string('requestspersecondactual', 'report_loadtest'); ?></th>
<th class="header c2" scope="col"><?php print_string('mediantime', 'report_loadtest'); ?></th>
<th class="header lastcol" scope="col"><?php print_string('successful', 'report_loadtest'); ?></th>
</tr>
</table>
<?php


        
// Finish the page
print_footer();
