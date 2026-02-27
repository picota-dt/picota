import solarPlant.main

# First arg is data directory:../temp/picota/workspace/32a497be-7330-41ac-8541-5934b9fbda7a/data
# Second the directory where the models are serialized

solarPlant.main.train("/Users/oroncal/workspace/projects/picota/runtime.test/data/solar_plant",
                      "/Users/oroncal/workspace/projects/picota/temp/test-models")
