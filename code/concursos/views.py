from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "concursos/lista.html")


def nuevo(request):
    return render(request, "concursos/nuevo.html")


def editar(request, id):
    return render(request, "concursos/editar.html")


def detalle(request, id):
    return render(request, "concursos/detalle.html")