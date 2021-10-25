"""
Upload annotations on current opened image.
Using "run for project" in script editor can process selected images automatically.

Format of json file should be: [Feature, Feature, Feature, ...]
("Feature" is a GeoJSON Feature. see https://geojson.org)
- Containing a list of "Feature" having no parent. 
- For each "Feature" or child "Feature", I resolve the hierarchy of objects so that there is a "property" 
  named "children" which is a list of Feature(empty list for "Feature" having no child).
"""
import com.google.gson.Gson
import com.google.gson.JsonArray

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

// default save path
def default_path = getCurrentServerPath().split("file:/")[-1].split("\\.")[0] + ".json"
// set saving path
def file_path = Dialogs.showInputDialog("Upload Annotations", "file path: ", default_path)
if (file_path == null || file_path == "") {
    Dialogs.showWarningNotification("Upload Annotations", "Please input annotation file path")
    return
}
def insertHierarchy = Dialogs.showConfirmDialog("Upload Annotations", "Reserve hierarchy of annotations?")

// Processing
def imageHierarchy = getCurrentHierarchy()
// parsing json file
var gson = GsonTools.getInstance()
File annoFile = new File(file_path)
var fileStream = Files.newInputStream(annoFile.toPath())
var fileReader = new InputStreamReader(new BufferedInputStream(fileStream))
var element = gson.fromJson(fileReader, JsonElement.class) // all content read in
var array = element.getAsJsonArray();
// convert to Pathobjects
var pathObjects = new ArrayList<PathObject>()
addChildRec(array, pathObjects, gson)
// show
println String.format("Total annotations: %d", pathObjects.size())
var result = imageHierarchy.addPathObjects(pathObjects)
println String.format("result: %s", result)
if (!result) {
    Dialogs.showErrorNotification("Upload Annotations", "Parsing successfully but upload failed!")
    return
}
if (insertHierarchy) {
    imageHierarchy.resolveHierarchy()
    println "reserve hierarchy of annotations."
}
