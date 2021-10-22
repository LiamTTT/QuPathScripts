"""
extract annotations on all images of current qupath project and save in json format. can not select images.

Format of json file: [Feature, Feature, Feature, ...]
("Feature" is a GeoJSON Feature. see https://geojson.org)
- Containing a list of "Feature" having no parent. 
- For each "Feature" or child "Feature", I resolve the hierarchy of objects so that there is a "property" 
  named "children" which is a list of Feature(empty list for "Feature" having no child).
"""

import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.io.GsonTools
import qupath.lib.objects.PathObject

import java.nio.file.Paths

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

def project = getProject()
def projPath = project.getPath()
// default save path
def default_dir = Paths.get(projPath.getParent().toString(), "annotations")
// set saving path
def save_dir = Dialogs.showInputDialog("Save Annotations", "save dir: ", default_dir.toString())
if (save_dir == null || save_dir == "") {
    Dialogs.showWarningNotification("Save Annotations", "Please input save path")
    return
}
File sd = new File(save_dir)
if (!sd.isDirectory()) {
    sd.mkdirs()
    Dialogs.showInfoNotification("Save Annotations", "Create save dir: " + save_dir)
}

def pretty = Dialogs.showConfirmDialog("Save Annotations", "save pretty? (this will enlarge size of json file)")
// processing
def gson = GsonTools.getInstance(pretty)
for (entry in project.getImageList()) {
    def save_path = Paths.get(save_dir, entry.getImageName().split("\\.")[0] + ".json")
    def imageHierarchy = entry.readHierarchy()
    def rootObj = imageHierarchy.getRootObject()
    // get all features and child features
    def annoGeoJsons = []
    for (annotation in imageHierarchy.getAnnotationObjects()) {
        if (annotation == rootObj || annotation.getParent() != rootObj) continue
        annoGeoJsons.add(getChildRec(annotation))
    }
    println annoGeoJsons.size() // print features having no parent.
    // save
    FileWriter writer = new FileWriter(save_path.toString())
    writer.write(gson.toJson(annoGeoJsons))
    writer.flush()
    writer.close()
    println "save in: " + save_path
    println "save pretty: %s".formatted(pretty)
}