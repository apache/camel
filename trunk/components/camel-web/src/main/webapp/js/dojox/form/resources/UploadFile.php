<?php
// summary
//		Test file to handle image uploads (remove the image size check to upload non-images)
//
//		This file handles both Flash and HTML uploads
//
//		NOTE: This is obviously a PHP file, and thus you need PHP running for this to work
//		NOTE: Directories must have write permissions
//		NOTE: This code uses the GD library (to get image sizes), that sometimes is not pre-installed in a 
//				standard PHP build. 
//
require("cLOG.php");
function findTempDirectory()
  {
    if(isset($_ENV["TMP"]) && is_writable($_ENV["TMP"])) return $_ENV["TMP"];
    elseif( is_writable(ini_get('upload_tmp_dir'))) return ini_get('upload_tmp_dir');
    elseif(isset($_ENV["TEMP"]) && is_writable($_ENV["TEMP"])) return $_ENV["TEMP"];
    elseif(is_writable("/tmp")) return "/tmp";
    elseif(is_writable("/windows/temp")) return "/windows/temp";
    elseif(is_writable("/winnt/temp")) return "/winnt/temp";
    else return null;
  }
function trace($txt, $isArray=false){
	//creating a text file that we can log to
	// this is helpful on a remote server if you don't
	//have access to the log files
	//
	//echo($txt."<br/>");
	$log = new cLOG("../resources/upload.txt", false);
	//$log->clear();
	if($isArray){
		$log->printr($txt);
	}else{
		$log->write($txt);
	}
}
function getImageType($filename){
	return strtolower(substr(strrchr($filename,"."),1));
}
trace("---------------------------------------------------------");
trace("TmpDir:".findTempDirectory());
//
//
//	EDIT ME: According to your local directory structure.
// 	NOTE: Folders must have write permissions
//
$upload_path = "../resources/"; 	// where image will be uploaded, relative to this file
$download_path = "../resources/";	// same folder as above, but relative to the HTML file

//
// 	NOTE: maintain this path for JSON services
//
require("../../../dojo/tests/resources/JSON.php");
$json = new Services_JSON();

//
// 	Determine if this is a Flash upload, or an HTML upload
//	
//

//		First combine relavant postVars
$postdata = array();
$data = "";
foreach ($_POST as $nm => $val) {
	$data .= $nm ."=" . $val . ",";
	$postdata[$nm] = $val;
}

$fieldName = "flashUploadFiles";//Filedata";

if( isset($_FILES[$fieldName])){
	//
	// If the data passed has $fieldName, then it's Flash. That's the default fieldname used.
	//
	trace("returnFlashdata.... ");
	
	trace("");
	trace("ID:");
	trace($_POST['testId']);
	
	trace("Flash POST:");
	trace($_POST, true);
	
	trace("POSTDATA:");
	trace($postdata, true);
	
	trace("GET:");
	trace($_GET, true);
	
	trace("FILES:");
	trace($_FILES[$fieldName], true);
	
	trace("REQUEST:");
	trace($_REQUEST, true);
	
	
	
	
	
	$returnFlashdata = true;
	$m = move_uploaded_file($_FILES[$fieldName]['tmp_name'],  $upload_path . $_FILES[$fieldName]['name']);
	$name = $_FILES[$fieldName]['name'];
	$file = $upload_path . $name;
	list($width, $height) = getimagesize($file);
	$type = getImageType($file);
	trace("file: " . $file ."  ".$type." ".$width);
	// 		Flash gets a string back:
	
	$data .='file='.$file.',name='.$name.',width='.$width.',height='.$height.',type='.$type;
	if($returnFlashdata){
		trace("returnFlashdata");
		trace($data, true);
		echo($data);
		return $data;
	}




}elseif( isset($_FILES['uploadedfile']) ){
	//
	// 	If the data passed has 'uploadedfile', then it's HTML. 
	//	There may be better ways to check this, but this is just a test file.$returnFlashdata = false;
	//
	$m = move_uploaded_file($_FILES['uploadedfile']['tmp_name'],  $upload_path . $_FILES['uploadedfile']['name']);
	trace("moved:".$m);
	trace("Temp:".$_FILES['uploadedfile']['tmp_name']);
	
	
	
	
	trace("HTML single POST:");
	trace($_POST, true);
	
	$name = $_FILES['uploadedfile']['name'];
	$file = $upload_path . $name;
	$type = getImageType($file);
	list($width, $height) = getimagesize($file);
	trace("file: " . $file );
	$ar = array(
		'file' => $file,
		'name' => $name,
		'width' => $width,
		'height' => $height,
		'type'=> $type
	);
	$postdata['file'] = $file;
	$postdata['name'] = $name;
	$postdata['width'] = $width;
	$postdata['height'] = $height;
	$postdata['type'] = $type;

}elseif( isset($_FILES['uploadedfile0']) ){
	//
	//	Multiple files have been passed from HTML
	//
	$cnt = 0;
	$ar = array();
	trace("HTML multiple POST:");
	trace($_POST, true);

	while(isset($_FILES['uploadedfile'.$cnt])){
		$moved = move_uploaded_file($_FILES['uploadedfile'.$cnt]['tmp_name'],  $upload_path . $_FILES['uploadedfile'.$cnt]['name']);
		if($moved){
			$name = $_FILES['uploadedfile'.$cnt]['name'];
			$file = $upload_path . $name;
			$type = getImageType($file);
			list($width, $height) = getimagesize($file);
			trace("file: " . $file );
			$ar[] = array(
				'file' => $file,
				'name' => $name,
				'width' => $width,
				'height' => $height,
				'type'=> $type
			);
					
			$postdata['file'] = $file;
			$postdata['name'] = $name;
			$postdata['width'] = $width;
			$postdata['height'] = $height;
			$postdata['type'] = $type;
		}
		$cnt++;
	}
	
}elseif(isset($_GET['rmFiles'])){
	trace("DELETING FILES" . $_GET['rmFiles']);
	$rmFiles = explode(";", $_GET['rmFiles']);
	foreach($rmFiles as $f){
		if($f && file_exists($f)){
			trace("deleted:" . $f. ":" .unlink($f));
		}
	}

}else{
	trace("IMROPER DATA SENT... $FILES:");
	trace($_FILES);
}

//HTML gets a json array back:
//$data = $json->encode($ar);
$data = $json->encode($postdata);
trace($data);
// in a text field:
?>
<textarea><?php print $data; ?></textarea>