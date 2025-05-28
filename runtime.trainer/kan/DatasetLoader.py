from kan.FloatCaster import register_cast


class DatasetLoader:
    def __init__(self, path, col_sep):
        self.path = path
        self.col_sep = col_sep

    def load(self):
        with open(self.path) as dataset:
            data = list(
                map(lambda row: list(map(lambda register: register_cast(register), row.split(self.col_sep))),
                    dataset.readlines()))
            head = data[0]
            dataframe = {column: [] for column in head}
            for index in range(len(head)):
                for row in data[1:]:
                    dataframe[head[index]].append(row[index])
        dataframe = {key: value for key, value in dataframe.items()}
        return dataframe
