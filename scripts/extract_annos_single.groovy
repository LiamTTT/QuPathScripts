"""
Extract annotations on current opened image and save in json formatted.
Using "run for project" in script editor can process selected images automatically.

Format of json file: [Feature, Feature, Feature, ...]
("Feature" is a GeoJSON Feature. see https://geojson.org)
- Containing a list of "Feature" having no parent. 
- For each "Feature" or child "Feature", I resolve the hierarchy of objects so that there is a "property" 
  named "children" which is a list of Feature(empty list for "Feature" having no child).
"""

import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.io.GsonTools
import qupath.lib.objects.PathObject

import static qupath.lib.gui.scripting.QPEx.*

// get child object and add to properties->children
def getChildRec(PathObject annotation) {
    def gson = GsonTools.getInstance()
    def anno = gson.fromJson(gson.toJson(annotation), HashMap.class)
    def children = annotation.getChildObjects()
    def childLs = []

    if (children.size()) {
        for (child in children) {
            childLs.add(getChildRec(child))
        }
    }
    anno["properties"]["children"] = childLs
    return anno
}

// default save path
def default_path = getCurrentServerPath().split("file:/")[-1].split("\\.")[0] + ".json"
// set saving path
def save_path = Dialogs.showInputDialog("Save Annotations", "save path: ", default_path)
if (save_path == null || save_path == "") {
    Dialogs.showWarningNotification("Save Annotations", "Please input save path")
    return
}
def pretty = Dialogs.showConfirmDialog("Save Annotations", "save pretty? (this will enlarge size of json file)")
// processing
def imageHierarchy = getCurrentHierarchy()
def gson = GsonTools.getInstance(pretty)
def rootObj = imageHierarchy.getRootObject()
// get all features and child features
def annoGeoJsons = []
for (annotation in getAnnotationObjects()) {
    if (annotation == rootObj || annotation.getParent() != rootObj) continue
    annoGeoJsons.add(getChildRec(annotation))
}
println annoGeoJsons.size() // print features having no parent.
// save
FileWriter writer = new FileWriter(save_path)
writer.write(gson.toJson(annoGeoJsons))
writer.flush()
writer.close()
println "save pretty: %s".formatted(pretty)
println "save in: " + save_path
