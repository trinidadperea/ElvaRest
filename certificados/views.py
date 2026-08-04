from django.shortcuts import render

# Create your views here.
def lista(request):
    return render(request, "certificados/lista.html")


def nuevo(request):
    return render(request, "certificados/nuevo.html")


def editar(request, id):
    return render(request, "certificados/editar.html")


def detalle(request, id):
    return render(request, "certificados/detalle.html")