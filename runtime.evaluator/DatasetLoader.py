from FloatCaster import register_cast

class DatasetLoader:
    def __init__(self, path, line_sep):
        self.path = path
        self.line_sep = line_sep

    def load(self):
        with open(self.path) as dataset:
            data = list(
                map(lambda row: list(map(lambda register: register_cast(register), row.split(self.line_sep))),
                    dataset.readlines()))
            head = data[0]
            dataframe = {column: [] for column in head}
            for index in range(len(head)):
                for row in data[1:]:
                    dataframe[head[index]].append(row[index])
        return dataframe