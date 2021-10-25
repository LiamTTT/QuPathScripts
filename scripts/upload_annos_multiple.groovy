"""
Upload annotations to current qupath project.

Format of json file should be: [Feature, Feature, Feature, ...]
The base name of json file should be same as image. 
("Feature" is a GeoJSON Feature. see https://geojson.org)
- Containing a list of "Feature" having no parent. 
- For each "Feature" or child "Feature", I resolve the hierarchy of objects so that there is a "property" 
  named "children" which is a list of Feature(empty list for "Feature" having no child).
"""
import com.google.gson.Gson
import com.google.gson.JsonArray

import java.nio.file.Paths

import static qupath.lib.gui.scripting.QPEx.*

import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.io.GsonTools
import qupath.lib.objects.PathObject

import java.nio.file.Files

import com.google.gson.JsonElement


// get child object
def addChildRec(JsonArray array, ArrayList<PathObject> pathObjects, Gson gson) {
    for (int i = 0; i < array.size(); i++) {
        var curJsonObj = array.get(i).getAsJsonObject()
        // add to list
        // todo siboliu 20211025: the default color should be setting properly.
        var curPathObj = gson.fromJson(curJsonObj, PathObject.class)
        pathObjects.add(curPathObj)
        // process children
        var childrenObjs = curJsonObj.get("properties").getAsJsonObject().get("children").getAsJsonArray()
        addChildRec(childrenObjs, pathObjects, gson)
    }
}

def project = getProject()
def projPath = project.getPath()
// default json dir
def default_dir = Paths.get(projPath.getParent().toString(), "annotations")
def fileDir = Dialogs.showInputDialog("Upload Annotations", "annotations file dir: ", default_dir.toString())
if (fileDir == null || fileDir == "") {
    Dialogs.showWarningNotification("Upload Annotations", "Please input annotation file dir")
    return
}
File sd = new File(fileDir)
if (!sd.isDirectory()) {
    Dialogs.showErrorNotification("Upload Annotations", "file dir: " + fileDir + " not exist!")
    return
}
def insertHierarchy = Dialogs.showConfirmDialog("Upload Annotations", "Reserve hierarchy of annotations?")
// Processing
var gson = GsonTools.getInstance()
for (entry in project.getImageList()) {
    println String.format("process %s.", entry.getImageName())
    def filePath = Paths.get(fileDir, entry.getImageName().split("\\.")[0] + ".json")
    println String.format("upload %s to %s ...", filePath, entry.getImageName())
    def imageData = entry.readImageData()
    def imageHierarchy = imageData.getHierarchy()
//    def imageHierarchy = entry.readImageData().getHierarchy()
    // parsing json file
    File annoFile = new File(filePath.toString())
    var fileStream = Files.newInputStream(annoFile.toPath())
    var fileReader = new InputStreamReader(new BufferedInputStream(fileStream))
    var element = gson.fromJson(fileReader, JsonElement.class) // all content read in
    var array = element.getAsJsonArray();
    // convert to Pathobjects
    var pathObjects = new ArrayList<PathObject>()
    addChildRec(array, pathObjects, gson)
    // show
    println String.format("Total annotations: %d", pathObjects.size())
    println String.format("result: %s", imageHierarchy.addPathObjects(pathObjects))
    if (insertHierarchy) {
        imageHierarchy.resolveHierarchy()
        println "reserve hierarchy of annotations."
    }
    entry.saveImageData(imageData)
}
